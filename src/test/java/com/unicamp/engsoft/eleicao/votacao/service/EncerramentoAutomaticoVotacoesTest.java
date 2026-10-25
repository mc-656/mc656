package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Job de encerramento por data de fim (EmVotacao → Apuracao), contra o banco real (#55).
 *
 * <p>Sem {@code @Transactional} de propósito: o job roda sem transação própria em produção, e uma
 * transação do teste esconderia a falta dela no {@code VotacaoStateService}. A limpeza é manual.
 */
class EncerramentoAutomaticoVotacoesTest extends AbstractIntegrationTest {

    /**
     * Ancorado na hora real: o {@code VotacaoStateService} usa o {@code Clock} do contexto e também
     * confere a data de fim, então o "agora" do job não pode estar à frente dele.
     */
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired VotacaoStateService stateService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired JdbcTemplate jdbc;

    private final List<Votacao> criadas = new ArrayList<>();

    private final List<Usuario> usuarios = new ArrayList<>();

    @AfterEach
    void limpa() {
        for (Votacao votacao : criadas) {
            jdbc.update("delete from transicoes_votacao where votacao_id = ?", votacao.getId());
            jdbc.update("delete from votacoes where id = ?", votacao.getId());
        }
        usuarios.forEach(usuario -> usuarioRepository.deleteById(usuario.getId()));
    }

    /** Job com relógio fixo; o agendamento automático fica desligado no profile de teste. */
    private EncerramentoAutomaticoVotacoes job() {
        return new EncerramentoAutomaticoVotacoes(
                votacaoRepository, stateService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /**
     * Semeia a votação direto no estado pedido: é preparação de cenário, não transição de negócio.
     */
    private Votacao votacao(EstadoVotacao estado, Instant fimEm) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        fimEm.minusSeconds(86400),
                        fimEm,
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        Votacao salva = votacaoRepository.saveAndFlush(votacao);
        criadas.add(salva);
        return salva;
    }

    private EstadoVotacao estadoDe(Votacao votacao) {
        return votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado();
    }

    @Test
    @DisplayName("Leva para apuração votação com fim atingido e audita com origem SISTEMA")
    void levaParaApuracaoComFimAtingido() {
        // Arrange
        Votacao noInstante = votacao(EstadoVotacao.EmVotacao, AGORA);
        Votacao atrasada = votacao(EstadoVotacao.EmVotacao, AGORA.minusSeconds(60));

        // Act
        job().encerrarVotacoesFinalizadas();

        // Assert
        for (Votacao votacao : List.of(noInstante, atrasada)) {
            assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.Apuracao);
            List<TransicaoVotacao> historico = stateService.historico(votacao.getId());
            assertThat(historico).hasSize(1);
            TransicaoVotacao auditoria = historico.getFirst();
            assertThat(auditoria.getOrigem()).isEqualTo(OrigemTransicao.SISTEMA);
            assertThat(auditoria.getAutorId()).isNull();
            assertThat(auditoria.getEstadoAnterior()).isEqualTo(EstadoVotacao.EmVotacao);
            assertThat(auditoria.getEstadoNovo()).isEqualTo(EstadoVotacao.Apuracao);
            assertThat(auditoria.getMotivo()).isEqualTo(EncerramentoAutomaticoVotacoes.MOTIVO);
        }
    }

    @Test
    @DisplayName("Não leva para apuração antes do fim nem votação fora de EmVotacao")
    void ignoraVotacoesForaDoCriterio() {
        // Arrange
        Votacao futura = votacao(EstadoVotacao.EmVotacao, AGORA.plusSeconds(1));
        Votacao publicada = votacao(EstadoVotacao.Publicada, AGORA.minusSeconds(60));
        Votacao cancelada = votacao(EstadoVotacao.Cancelada, AGORA.minusSeconds(60));

        // Act
        job().encerrarVotacoesFinalizadas();

        // Assert
        assertThat(estadoDe(futura)).isEqualTo(EstadoVotacao.EmVotacao);
        assertThat(estadoDe(publicada)).isEqualTo(EstadoVotacao.Publicada);
        assertThat(estadoDe(cancelada)).isEqualTo(EstadoVotacao.Cancelada);
        assertThat(stateService.historico(futura.getId())).isEmpty();
    }

    @Test
    @DisplayName("Votação suspensa com fim vencido continua suspensa até ação do admin")
    void suspensaComFimVencidoContinuaSuspensa() {
        // Arrange
        Votacao suspensa = votacao(EstadoVotacao.Suspensa, AGORA.minusSeconds(3600));

        // Act
        job().encerrarVotacoesFinalizadas();

        // Assert
        assertThat(estadoDe(suspensa)).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(stateService.historico(suspensa.getId())).isEmpty();
    }

    @Test
    @DisplayName("Retomar sem prorrogar com fim vencido leva à apuração na rodada seguinte do job")
    void retomadaSemProrrogarVaiParaApuracao() {
        // Arrange
        Usuario admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        usuarios.add(admin);
        Votacao suspensa = votacao(EstadoVotacao.Suspensa, AGORA.minusSeconds(3600));
        stateService.transitar(suspensa.getId(), EstadoVotacao.EmVotacao, admin.getId(), null);

        // Act
        job().encerrarVotacoesFinalizadas();

        // Assert
        assertThat(estadoDe(suspensa)).isEqualTo(EstadoVotacao.Apuracao);
        assertThat(stateService.historico(suspensa.getId()))
                .extracting(TransicaoVotacao::getEstadoNovo, TransicaoVotacao::getOrigem)
                .containsExactly(
                        tuple(EstadoVotacao.EmVotacao, OrigemTransicao.USUARIO),
                        tuple(EstadoVotacao.Apuracao, OrigemTransicao.SISTEMA));
    }
}
