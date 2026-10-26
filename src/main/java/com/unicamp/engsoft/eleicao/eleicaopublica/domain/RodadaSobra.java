package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import java.util.List;

/**
 * Uma rodada da distribuição das sobras (art. 109): as médias dos partidos que disputaram a vaga e
 * quem a levou. {@code clausulaDesempenho} indica se só concorreram partidos com 80% do quociente
 * eleitoral e candidatos com 20% dele.
 */
public record RodadaSobra(
        int numero, boolean clausulaDesempenho, List<MediaPartido> medias, CandidatoEleito eleito) {

    public RodadaSobra {
        medias = List.copyOf(medias);
    }
}
