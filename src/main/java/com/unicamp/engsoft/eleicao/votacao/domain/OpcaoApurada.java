package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Totalização dos votos apurados para uma opção específica (candidato, chapa ou proposta).
 *
 * @param opcaoId Identificador da opção votada.
 * @param totalVotos Soma ponderada dos votos recebidos por esta opção.
 * @param percentualVotosValidos Percentual de votos válidos obtidos (escala de 0 a 100).
 */
public record OpcaoApurada(
        UUID opcaoId, BigDecimal totalVotos, BigDecimal percentualVotosValidos) {}
