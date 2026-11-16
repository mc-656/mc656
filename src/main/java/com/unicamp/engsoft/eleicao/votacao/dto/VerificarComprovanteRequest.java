package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Comprovante a verificar (RNF-01). Vai no corpo, e não na URL, para não ficar em logs de acesso.
 */
public record VerificarComprovanteRequest(@NotBlank String comprovante) {}
