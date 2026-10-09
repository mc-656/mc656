package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Job de abertura por data de início (Publicada → EmVotacao), contra o banco real (#24). */
@Transactional
class AberturaAutomaticaVotacoesTest extends AbstractIntegrationTest {

    private static final Instant AGORA = Instant.parse("2030-01-01T12:00:00Z");

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired VotacaoStateService stateService;

    /** Job com relógio fixo; o agendamento automático fica desligado no profile de teste. */
    private AberturaAutomaticaVotacoes job() {
        return new AberturaAutomaticaVotacoes(
                votacaoRepository, stateService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /**
     * Semeia a votação direto no estado pedido: é preparação de cenário, não transição de negócio.
     */
    private Votacao votacao(EstadoVotacao estado, Instant inicioEm) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        inicioEm,
                        inicioEm.plusSeconds(86400),
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        return votacaoRepository.saveAndFlush(votacao);
    }

    @Test
    @DisplayName("Abre votação publicada com início atingido e audita com origem SISTEMA")
    void abreVotacaoComInicioAtingido() {
        // Arrange
        Votacao noInstante = votacao(EstadoVotacao.Publicada, AGORA);
        Votacao atrasada = votacao(EstadoVotacao.Publicada, AGORA.minusSeconds(60));

        // Act
        job().abrirVotacoesIniciadas();

        // Assert
        for (Votacao votacao : List.of(noInstante, atrasada)) {
            assertThat(votacaoRepository.findById(votacao.getId()))
                    .get()
                    .extracting(Votacao::getEstado)
                    .isEqualTo(EstadoVotacao.EmVotacao);
            List<TransicaoVotacao> historico = stateService.historico(votacao.getId());
            assertThat(historico).hasSize(1);
            TransicaoVotacao auditoria = historico.getFirst();
            assertThat(auditoria.getOrigem()).isEqualTo(OrigemTransicao.SISTEMA);
            assertThat(auditoria.getAutorId()).isNull();
            assertThat(auditoria.getEstadoAnterior()).isEqualTo(EstadoVotacao.Publicada);
            assertThat(auditoria.getEstadoNovo()).isEqualTo(EstadoVotacao.EmVotacao);
            assertThat(auditoria.getMotivo()).isEqualTo(AberturaAutomaticaVotacoes.MOTIVO);
        }
    }

    @Test
    @DisplayName("Não abre votação antes do início nem votação que não está publicada")
    void ignoraVotacoesForaDoCriterio() {
        // Arrange
        Votacao futura = votacao(EstadoVotacao.Publicada, AGORA.plusSeconds(1));
        Votacao rascunho = votacao(EstadoVotacao.RascunhoVotacao, AGORA.minusSeconds(60));
        Votacao cancelada = votacao(EstadoVotacao.Cancelada, AGORA.minusSeconds(60));

        // Act
        job().abrirVotacoesIniciadas();

        // Assert
        assertThat(votacaoRepository.findById(futura.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Publicada);
        assertThat(votacaoRepository.findById(rascunho.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.RascunhoVotacao);
        assertThat(votacaoRepository.findById(cancelada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Cancelada);
        assertThat(stateService.historico(futura.getId())).isEmpty();
    }
}
