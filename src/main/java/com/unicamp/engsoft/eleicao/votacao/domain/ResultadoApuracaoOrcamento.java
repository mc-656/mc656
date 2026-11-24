package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.List;

/** Resultado completo da apuração por cotas do orçamento participativo (RF-23). */
public record ResultadoApuracaoOrcamento(
        StatusApuracao status,
        BigDecimal orcamentoTotal,
        BigDecimal orcamentoUtilizado,
        BigDecimal saldoRestante,
        List<PropostaApurada> propostas,
        BigDecimal totalPontosComputados,
        long totalParticipantes,
        long totalEleitoresAptos,
        BigDecimal percentualComparecimento,
        boolean quorumAtingido) {

    public ResultadoApuracaoOrcamento {
        propostas = List.copyOf(propostas);
    }
}
