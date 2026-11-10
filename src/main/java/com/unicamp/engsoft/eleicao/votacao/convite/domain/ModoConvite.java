package com.unicamp.engsoft.eleicao.votacao.convite.domain;

/**
 * Quem pode entrar pelo link de convite (RF-17), escolhido pelo {@code ADMIN_VOTACAO}.
 *
 * <p>Persistido por nome ({@code VARCHAR(10)} com {@code CHECK} na V18).
 */
public enum ModoConvite {
    /** Qualquer usuário autenticado que tenha o link. */
    ABERTO,
    /** Só os e-mails da lista do convite; os demais recebem acesso negado. */
    RESTRITO
}
