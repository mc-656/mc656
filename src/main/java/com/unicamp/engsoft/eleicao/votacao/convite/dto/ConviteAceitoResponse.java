package com.unicamp.engsoft.eleicao.votacao.convite.dto;

import java.util.UUID;

/**
 * Resultado de abrir o link de convite (RF-17). {@code jaEraEleitor} diferencia a primeira entrada
 * de uma repetida, que também é sucesso.
 */
public record ConviteAceitoResponse(UUID votacaoId, String nomeVotacao, boolean jaEraEleitor) {}
