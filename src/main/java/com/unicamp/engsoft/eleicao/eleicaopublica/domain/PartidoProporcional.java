package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Partido (ou federação) numa eleição proporcional (RF-32).
 *
 * <p>A votação do partido soma os votos de legenda e os nominais de todos os seus candidatos: é
 * esse total, e não o voto de cada candidato, que define quantas cadeiras o partido leva (art. 107
 * do Código Eleitoral). Daí vem o caso contraintuitivo que a explicação quer mostrar.
 */
public record PartidoProporcional(
        String sigla, long votosLegenda, List<CandidatoProporcional> candidatos) {

    public PartidoProporcional {
        if (sigla == null || sigla.isBlank()) {
            throw new DadosInvalidosException("O partido precisa de uma sigla.");
        }
        sigla = sigla.strip();
        if (votosLegenda < 0) {
            throw new DadosInvalidosException(
                    "O partido " + sigla + " tem votos de legenda negativos.");
        }
        if (candidatos == null || candidatos.stream().anyMatch(Objects::isNull)) {
            throw new DadosInvalidosException(
                    "A lista de candidatos do partido " + sigla + " é inválida.");
        }
        // Nomes únicos no partido: é por (sigla, nome) que a explicação marca quem foi eleito.
        long nomesDistintos =
                candidatos.stream().map(c -> c.nome().toLowerCase(Locale.ROOT)).distinct().count();
        if (nomesDistintos != candidatos.size()) {
            throw new DadosInvalidosException(
                    "O partido " + sigla + " tem candidatos com o mesmo nome.");
        }
        candidatos = List.copyOf(candidatos);
    }

    public long votosNominais() {
        return candidatos.stream().mapToLong(CandidatoProporcional::votos).sum();
    }

    public long votosTotais() {
        return votosLegenda + votosNominais();
    }
}
