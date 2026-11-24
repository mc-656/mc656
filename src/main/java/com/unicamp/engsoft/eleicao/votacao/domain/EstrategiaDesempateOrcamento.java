package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.List;

/**
 * Ordena propostas empatadas em pontuação no orçamento participativo em subgrupos ordenados; um
 * subgrupo com mais de um elemento representa empate não resolvido (#95).
 */
@FunctionalInterface
public interface EstrategiaDesempateOrcamento {
    List<List<PropostaApuravel>> ordenarEmpatadas(List<PropostaApuravel> empatadas);
}
