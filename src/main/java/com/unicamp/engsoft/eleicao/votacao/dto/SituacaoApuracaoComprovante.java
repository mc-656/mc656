package com.unicamp.engsoft.eleicao.votacao.dto;

/**
 * Situação da apuração para uma cédula cujo registro foi confirmado (RNF-01, #83).
 *
 * <p>{@link #INCLUIDA_NA_APURACAO} exige evidência persistida ({@code Apuracao} no banco), nunca é
 * inferida só pelo estado da votação.
 */
public enum SituacaoApuracaoComprovante {
    NAO_CONFIRMADA,
    INCLUIDA_NA_APURACAO,
    VOTACAO_ANULADA,
    VOTACAO_CANCELADA
}
