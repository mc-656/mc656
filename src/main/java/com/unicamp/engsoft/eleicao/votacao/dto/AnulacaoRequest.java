package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de anulação por irregularidade (RF-42). A justificativa é obrigatória e vai para a
 * auditoria da transição ({@code transicoes_votacao.motivo}, até 500 caracteres).
 */
public record AnulacaoRequest(@NotBlank @Size(max = 500) String justificativa) {}
