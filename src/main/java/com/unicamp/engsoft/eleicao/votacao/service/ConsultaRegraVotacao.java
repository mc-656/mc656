package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.util.Optional;

/**
 * Ponto único de leitura da {@link RegraVotacao} de uma votação.
 *
 * <p>Existe porque a persistência das regras (#21, PR #97) ainda não está em {@code develop}, mas o
 * cadastro de eleitores (RF-10) já precisa saber se a votação usa voto ponderado. Quem consome
 * depende desta interface, e só a implementação muda quando {@code Votacao#getRegras()} existir.
 */
public interface ConsultaRegraVotacao {

    /** Regra configurada da votação, ou vazio se ainda não foi configurada. */
    Optional<RegraVotacao> regraDe(Votacao votacao);
}
