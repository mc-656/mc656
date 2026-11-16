package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.ModeloVotacao;
import java.util.List;

/**
 * Votação recém-criada por modelo (RF-16), já com as regras e opções aplicadas, para a tela de
 * revisão mostrar tudo sem novas consultas antes de publicar.
 */
public record VotacaoPorModeloResponse(
        VotacaoResponse votacao,
        ModeloVotacao modelo,
        RegraVotacaoResponse regras,
        List<CandidatoResponse> opcoes) {}
