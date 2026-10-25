package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Números da apuração que não dependem do desfecho (RF-15, RNF-05).
 *
 * <p>São calculados uma única vez pelo motor, antes de decidir o status, e copiados para qualquer
 * {@link ResultadoApuracao}. Agrupá-los evita repassar os mesmos cinco valores entre os passos da
 * apuração e impede que {@code totalParticipantes} e {@code totalEleitoresAptos}, dois {@code long}
 * vizinhos, sejam trocados de posição sem aviso do compilador.
 *
 * @param ranking Desempenho de cada opção, ordenado decrescente pelos votos recebidos.
 * @param totalVotosValidos Soma ponderada de todos os votos válidos apurados.
 * @param totalParticipantes Quantidade de eleitores que compareceram e votaram.
 * @param totalEleitoresAptos Quantidade total de eleitores aptos a votar.
 * @param percentualComparecimento Percentual de comparecimento em relação aos eleitores aptos.
 */
public record Totalizacao(
        List<OpcaoApurada> ranking,
        BigDecimal totalVotosValidos,
        long totalParticipantes,
        long totalEleitoresAptos,
        BigDecimal percentualComparecimento) {

    public Totalizacao {
        ranking = ranking == null ? List.of() : List.copyOf(ranking);
    }
}
