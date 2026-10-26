package com.unicamp.engsoft.eleicao.votacao.domain;

/**
 * Quem disparou uma transição de estado (RNF-03). {@code SISTEMA} cobre as transições por data,
 * feitas por job agendado e sem autor.
 */
public enum OrigemTransicao {
    USUARIO,
    SISTEMA
}
