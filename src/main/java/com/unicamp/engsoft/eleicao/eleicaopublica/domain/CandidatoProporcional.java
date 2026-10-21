package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.util.Comparator;

/**
 * Candidato de uma eleição proporcional (deputados e vereadores) com seus votos nominais (RF-32).
 *
 * <p>Nome próprio do módulo de eleições públicas: não reaproveita {@code CandidatoOuChapa} de
 * {@code votacao} para que os dois domínios nunca compartilhem modelo nem tabelas.
 */
public record CandidatoProporcional(String nome, long votos) {

    /**
     * Ordem em que o partido preenche suas cadeiras: mais votado primeiro. O desempate por nome só
     * torna o resultado determinístico (a lei desempata pela idade, dado que não temos).
     */
    public static final Comparator<CandidatoProporcional> MAIS_VOTADO_PRIMEIRO =
            Comparator.comparingLong(CandidatoProporcional::votos)
                    .reversed()
                    .thenComparing(CandidatoProporcional::nome);

    public CandidatoProporcional {
        if (nome == null || nome.isBlank()) {
            throw new DadosInvalidosException("O candidato precisa de um nome.");
        }
        nome = nome.strip();
        if (votos < 0) {
            throw new DadosInvalidosException("O candidato " + nome + " tem votos negativos.");
        }
    }
}
