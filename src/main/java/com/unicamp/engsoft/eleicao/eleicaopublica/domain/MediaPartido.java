package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import java.math.BigDecimal;

/**
 * Média de um partido numa rodada de sobras: votos / (cadeiras já obtidas + 1), art. 109, I.
 *
 * <p>A média vai arredondada em duas casas só para exibição; a calculadora compara partidos com
 * aritmética inteira, para que o arredondamento nunca decida uma cadeira.
 */
public record MediaPartido(String partido, long votos, int cadeirasAntes, BigDecimal media) {}
