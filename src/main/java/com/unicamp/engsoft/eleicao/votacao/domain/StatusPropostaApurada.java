package com.unicamp.engsoft.eleicao.votacao.domain;

/** Resultado individual de cada proposta após a apuração por cotas (RF-23). */
public enum StatusPropostaApurada {
    SELECIONADA,
    NAO_SELECIONADA_ORCAMENTO,
    EMPATADA_SEM_DECISAO,
    QUORUM_NAO_ATINGIDO
}
