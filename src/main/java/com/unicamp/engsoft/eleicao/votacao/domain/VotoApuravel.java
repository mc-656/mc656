package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record que representa a unidade básica de entrada para o motor de apuração.
 *
 * @param opcaoId Identificador da opção votada (candidato, chapa ou proposta).
 * @param peso Peso do voto ou quantidade de pontos alocados à opção.
 */
public record VotoApuravel(
    UUID opcaoId,
    BigDecimal peso
) {
}