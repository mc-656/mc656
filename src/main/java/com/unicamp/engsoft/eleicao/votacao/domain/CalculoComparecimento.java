package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo puro e exato de comparecimento e quórum mínimo em uma votação (RF-11, RF-40).
 *
 * <p>Contado sempre por eleitor apto e não por peso de voto (SPECS §4.1). Usa aritmética exata
 * ({@code participantes * 100 >= quorum * aptos}) para evitar aprovações falsas por arredondamento
 * (ex.: 2 de 3 = 66,67% arredondado não atinge quórum de 66,67%).
 */
public final class CalculoComparecimento {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private CalculoComparecimento() {}

    public static BigDecimal percentual(long aptos, long participantes) {
        if (aptos <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(participantes)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(aptos), 2, RoundingMode.HALF_UP);
    }

    public static boolean quorumAtingido(
            BigDecimal quorumMinimoPercentual, long aptos, long participantes) {
        if (quorumMinimoPercentual == null) {
            return true;
        }
        if (aptos <= 0) {
            return BigDecimal.ZERO.compareTo(quorumMinimoPercentual) >= 0;
        }
        return BigDecimal.valueOf(participantes)
                        .multiply(CEM)
                        .compareTo(quorumMinimoPercentual.multiply(BigDecimal.valueOf(aptos)))
                >= 0;
    }
}
