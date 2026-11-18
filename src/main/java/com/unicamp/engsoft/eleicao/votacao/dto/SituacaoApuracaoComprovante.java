package com.unicamp.engsoft.eleicao.votacao.dto;

/**
 * Situação da apuração para uma cédula cujo registro foi confirmado.
 *
 * <p>Não há confirmação de inclusão enquanto a apuração não fornecer evidência persistida do
 * processamento da cédula (#83). O estado da votação, isoladamente, não é essa evidência.
 */
public enum SituacaoApuracaoComprovante {
    NAO_CONFIRMADA,
    VOTACAO_ANULADA,
    VOTACAO_CANCELADA
}
