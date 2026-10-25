package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da apuração de uma votação conforme as regras configuradas (RF-15, RNF-05).
 *
 * <p>Os resultados devem ser criados pelos métodos de fábrica ({@link #definido}, {@link
 * #segundoTurno}, {@link #empate}, {@link #semVencedor} e {@link #quorumNaoAtingido}), e não pelo
 * construtor posicional: cada fábrica preenche só os campos que fazem sentido para o seu status e
 * copia os números comuns de uma {@link Totalizacao}.
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

    /** Uma opção venceu segundo o critério de maioria da regra. */
    public static ResultadoApuracao definido(UUID vencedorId, Totalizacao totalizacao) {
        return de(StatusApuracao.DEFINIDO, vencedorId, List.of(), totalizacao, true);
    }

    /** Nenhuma opção atingiu o critério e as duas finalistas disputam o 2º turno. */
    public static ResultadoApuracao segundoTurno(List<UUID> finalistas, Totalizacao totalizacao) {
        return de(StatusApuracao.SEGUNDO_TURNO, null, finalistas, totalizacao, true);
    }

    /** Opções empatadas impedem definir vencedor ou finalistas. */
    public static ResultadoApuracao empate(Totalizacao totalizacao) {
        return de(StatusApuracao.EMPATE, null, List.of(), totalizacao, true);
    }

    /** Nenhuma opção atingiu o critério e a regra não prevê 2º turno (ou não há votos). */
    public static ResultadoApuracao semVencedor(Totalizacao totalizacao) {
        return de(StatusApuracao.SEM_VENCEDOR, null, List.of(), totalizacao, true);
    }

    /** O comparecimento ficou abaixo do quórum mínimo; o ranking é informado mas não decide. */
    public static ResultadoApuracao quorumNaoAtingido(Totalizacao totalizacao) {
        return de(StatusApuracao.QUORUM_NAO_ATINGIDO, null, List.of(), totalizacao, false);
    }

    private static ResultadoApuracao de(
            StatusApuracao status,
            UUID vencedorId,
            List<UUID> finalistas,
            Totalizacao totalizacao,
            boolean quorumAtingido) {
        return new ResultadoApuracao(
                status,
                vencedorId,
                finalistas,
                totalizacao.ranking(),
                totalizacao.totalVotosValidos(),
                totalizacao.totalParticipantes(),
                totalizacao.totalEleitoresAptos(),
                totalizacao.percentualComparecimento(),
                quorumAtingido);
    }
}
