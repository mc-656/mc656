package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.ModeloVotacao;
import java.util.List;

/** Modelo pronto oferecido na tela de criação (RF-16), com as regras que ele preenche. */
public record ModeloVotacaoResponse(
        ModeloVotacao codigo,
        String nome,
        String descricao,
        RegraVotacaoResponse regras,
        boolean votoSecreto,
        List<String> opcoesPadrao) {

    public static ModeloVotacaoResponse of(ModeloVotacao modelo) {
        return new ModeloVotacaoResponse(
                modelo,
                modelo.nome(),
                modelo.descricao(),
                RegraVotacaoResponse.of(modelo.regra()),
                modelo.votoSecreto(),
                modelo.opcoesPadrao());
    }
}
