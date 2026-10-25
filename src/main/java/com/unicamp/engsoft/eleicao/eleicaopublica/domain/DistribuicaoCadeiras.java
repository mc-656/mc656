package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import java.util.List;

/**
 * Resultado completo de uma distribuição proporcional. Cada etapa fica preservada para que a
 * explicação didática (RF-32) mostre o passo a passo, e não só quem foi eleito.
 */
public record DistribuicaoCadeiras(
        long votosValidos,
        int vagas,
        long quocienteEleitoral,
        boolean nenhumPartidoAtingiuQuociente,
        List<ResultadoPartido> partidos,
        List<RodadaSobra> rodadasSobras,
        List<CandidatoEleito> eleitos) {

    public DistribuicaoCadeiras {
        partidos = List.copyOf(partidos);
        rodadasSobras = List.copyOf(rodadasSobras);
        eleitos = List.copyOf(eleitos);
    }

    /** Cadeiras que ficaram vazias por falta de candidatos (raro, mas possível). */
    public int vagasNaoPreenchidas() {
        return vagas - eleitos.size();
    }
}
