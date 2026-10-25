package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Leva para apuração as votações em andamento cuja data de fim chegou ({@code EmVotacao →
 * Apuracao}, SPECS §4.2, #55). A transição sai sem autor, então a auditoria registra origem {@code
 * SISTEMA} (RNF-03).
 *
 * <p>Só consulta {@code EmVotacao}: uma votação {@code Suspensa} com o fim vencido fica parada até
 * o admin agir. Se ele retomar sem prorrogar, ela volta a {@code EmVotacao} com o fim vencido e a
 * rodada seguinte deste job a leva para {@code Apuracao}.
 *
 * <p>Sem {@code @Transactional} de propósito, como em {@link AberturaAutomaticaVotacoes}: cada
 * votação transita na sua própria transação, e uma falha (ex.: admin suspendeu entre a consulta e a
 * transição) não desfaz o encerramento das demais.
 */
@Component
public class EncerramentoAutomaticoVotacoes {

    private static final Logger log = LoggerFactory.getLogger(EncerramentoAutomaticoVotacoes.class);

    static final String MOTIVO = "Data de fim atingida";

    private final VotacaoRepository votacaoRepository;
    private final VotacaoStateService stateService;
    private final Clock clock;

    public EncerramentoAutomaticoVotacoes(
            VotacaoRepository votacaoRepository, VotacaoStateService stateService, Clock clock) {
        this.votacaoRepository = votacaoRepository;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${votacao.agendador.intervalo:PT1M}")
    public void encerrarVotacoesFinalizadas() {
        for (UUID votacaoId :
                votacaoRepository.idsPorEstadoComFimAte(EstadoVotacao.EmVotacao, clock.instant())) {
            try {
                stateService.transitar(votacaoId, EstadoVotacao.Apuracao, null, MOTIVO);
            } catch (DomainException e) {
                log.warn(
                        "Não foi possível levar a votação {} para apuração: {}",
                        votacaoId,
                        e.getMessage());
            } catch (RuntimeException e) {
                // Falha de infraestrutura (lock, banco) numa votação não pode deixar as demais
                // abertas depois do fim nesta rodada.
                log.error("Erro ao levar a votação {} para apuração", votacaoId, e);
            }
        }
    }
}
