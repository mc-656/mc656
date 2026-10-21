package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da apuração de uma votação conforme as regras configuradas (RF-15, RNF-05).
 *
 * @param status Desfecho da apuração.
 * @param vencedorId Identificador da opção vencedora (presente apenas se {@code status ==
 *     DEFINIDO}).
 * @param finalistasSegundoTurno Identificadores das opções que avançam ao 2º turno (presente apenas
 *     se {@code status == SEGUNDO_TURNO}).
 * @param ranking Lista com o desempenho de cada opção, ordenada decrescente pelos votos recebidos.
 * @param totalVotosValidos Soma ponderada de todos os votos válidos apurados.
 * @param totalParticipantes Quantidade de eleitores que compareceram e votaram.
 * @param totalEleitoresAptos Quantidade total de eleitores aptos a votar.
 * @param percentualComparecimento Percentual de comparecimento em relação aos eleitores aptos.
 * @param quorumAtingido Indica se o comparecimento atingiu o quórum mínimo exigido pela regra.
 */
public record ResultadoApuracao(
        StatusApuracao status,
        UUID vencedorId,
        List<UUID> finalistasSegundoTurno,
        List<OpcaoApurada> ranking,
        BigDecimal totalVotosValidos,
        long totalParticipantes,
        long totalEleitoresAptos,
        BigDecimal percentualComparecimento,
        boolean quorumAtingido) {

    public ResultadoApuracao {
        finalistasSegundoTurno =
                finalistasSegundoTurno == null ? List.of() : List.copyOf(finalistasSegundoTurno);
        ranking = ranking == null ? List.of() : List.copyOf(ranking);
    }
}
