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
 * Abre as votações publicadas cuja data de início chegou ({@code Publicada → EmVotacao}, SPECS
 * §4.2). A transição sai sem autor, então a auditoria registra origem {@code SISTEMA}.
 *
 * <p>Sem {@code @Transactional} de propósito: cada votação transita na sua própria transação, e uma
 * falha (ex.: admin cancelou entre a consulta e a transição) não desfaz a abertura das demais.
 */
@Component
public class AberturaAutomaticaVotacoes {

    private static final Logger log = LoggerFactory.getLogger(AberturaAutomaticaVotacoes.class);

    static final String MOTIVO = "Data de início atingida";

    private final VotacaoRepository votacaoRepository;
    private final VotacaoStateService stateService;
    private final Clock clock;

    public AberturaAutomaticaVotacoes(
            VotacaoRepository votacaoRepository, VotacaoStateService stateService, Clock clock) {
        this.votacaoRepository = votacaoRepository;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${votacao.agendador.intervalo:PT1M}")
    public void abrirVotacoesIniciadas() {
        for (UUID votacaoId :
                votacaoRepository.idsPorEstadoComInicioAte(
                        EstadoVotacao.Publicada, clock.instant())) {
            try {
                stateService.transitar(votacaoId, EstadoVotacao.EmVotacao, null, MOTIVO);
            } catch (DomainException e) {
                log.warn("Não foi possível abrir a votação {}: {}", votacaoId, e.getMessage());
            }
        }
    }
}
