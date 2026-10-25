package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

/**
 * Resultado de um partido na distribuição de cadeiras (RF-32).
 *
 * <p>{@code cadeirasPorSobras} conta toda vaga que não veio do quociente partidário: as das maiores
 * médias e, quando nenhum partido atinge o quociente eleitoral, as dos mais votados (art. 111).
 */
public record ResultadoPartido(
        PartidoProporcional partido,
        long quocientePartidario,
        int cadeirasPorQuociente,
        int cadeirasPorSobras,
        boolean atingiuClausulaDesempenho) {

    public int cadeirasTotais() {
        return cadeirasPorQuociente + cadeirasPorSobras;
    }
}
