package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.votacao.domain.*;
import com.unicamp.engsoft.eleicao.votacao.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Sem transação do teste: verifica commits, rollback real e locks entre conexões diferentes. */
class ApuracaoAtomicidadeTest extends AbstractIntegrationTest {
    @Autowired ApuracaoService apuracao;
    @Autowired VotacaoStateService estados;
    @Autowired VotacaoRepository votacoes;
    @Autowired ApuracaoRepository snapshots;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AnulacaoVotacaoService anulacao;
    private final List<UUID> criadas = new ArrayList<>();

    private Votacao criar(EstadoVotacao estado, BigDecimal quorum) {
        Votacao v =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(1),
                        "Teste",
                        "Atomicidade");
        v.configurarRegras(
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, quorum, false));
        v.alterarEstado(estado);
        v = votacoes.saveAndFlush(v);
        criadas.add(v.getId());
        return v;
    }

    @AfterEach
    void limpar() {
        for (UUID id : criadas) {
            jdbc.update("delete from apuracoes where votacao_id = ?", id);
            jdbc.update("delete from transicoes_votacao where votacao_id = ?", id);
            jdbc.update("delete from votacoes where id = ?", id);
        }
    }

    @Test
    void falhaDepoisDoSnapshotDesfazApuracaoETransicao() {
        Votacao v = criar(EstadoVotacao.EmVotacao, BigDecimal.TEN);
        doThrow(new IllegalStateException("falha de infraestrutura"))
                .when(anulacao)
                .anularPorFaltaDeQuorum(v.getId());
        assertThatThrownBy(() -> estados.transitar(v.getId(), EstadoVotacao.Apuracao, null, "Fim"))
                .hasMessageContaining("falha de infraestrutura");
        assertThat(snapshots.findById(v.getId())).isEmpty();
        assertThat(estados.historico(v.getId())).isEmpty();
        assertThat(votacoes.findById(v.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.EmVotacao);
        // A próxima tentativa do job pode concluir normalmente.
        doCallRealMethod().when(anulacao).anularPorFaltaDeQuorum(v.getId());
        estados.transitar(v.getId(), EstadoVotacao.Apuracao, null, "Fim");
        assertThat(snapshots.findById(v.getId())).isPresent();
        assertThat(votacoes.findById(v.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Anulada);
    }

    @Test
    void chamadasConcorrentesRetornamMesmoSnapshot() throws Exception {
        Votacao v = criar(EstadoVotacao.Apuracao, null);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        Callable<Apuracao> tarefa =
                () -> {
                    prontas.countDown();
                    if (!iniciar.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timeout");
                    return apuracao.apurar(v.getId());
                };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Apuracao> primeira = executor.submit(tarefa);
            Future<Apuracao> segunda = executor.submit(tarefa);
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            Apuracao a = primeira.get(15, TimeUnit.SECONDS);
            Apuracao b = segunda.get(15, TimeUnit.SECONDS);
            // Postgres armazena microssegundos; a primeira resposta ainda pode conter nanos da JVM.
            assertThat(a.getConcluidaEm().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                    .isEqualTo(
                            b.getConcluidaEm().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            assertThat(a.getResultado()).isEqualTo(b.getResultado());
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from apuracoes where votacao_id = ?",
                                    Long.class,
                                    v.getId()))
                    .isEqualTo(1);
        }
    }
}
