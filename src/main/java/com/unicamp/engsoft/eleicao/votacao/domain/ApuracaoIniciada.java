package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.UUID;

/** Evento síncrono: a apuração participa da transação que encerra o recebimento de votos. */
public record ApuracaoIniciada(UUID votacaoId) {}
