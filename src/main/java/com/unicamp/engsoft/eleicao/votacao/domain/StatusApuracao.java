package com.unicamp.engsoft.eleicao.votacao.domain;

/** Desfecho da apuração de uma votação conforme as regras configuradas (RF-15). */
public enum StatusApuracao {
    /** Votação concluída com vencedor definido. */
    DEFINIDO,

    /** Nenhum candidato atingiu a maioria exigida e a regra prevê segundo turno. */
    SEGUNDO_TURNO,

    /** Empate que impede a definição de um vencedor ou dos finalistas do 2º turno. */
    EMPATE,

    /** O comparecimento de eleitores não atingiu o quórum mínimo exigido. */
    QUORUM_NAO_ATINGIDO,

    /** Nenhum candidato atingiu o critério de maioria e a regra não prevê segundo turno. */
    SEM_VENCEDOR
}
