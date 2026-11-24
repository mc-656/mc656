package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Acompanhamento agregado do comparecimento durante {@code EmVotacao} ou {@code Suspensa} (RF-40).
 * Nunca expõe contagem por opção nem identidade de eleitores (RNF-02).
 */
public record ComparecimentoResponse(
        UUID votacaoId,
        EstadoVotacao estado,
        long totalParticipacoes,
        long totalEleitoresAptos,
        BigDecimal percentualComparecimento,
        BigDecimal quorumMinimoPercentual,
        boolean quorumAtingido) {}
