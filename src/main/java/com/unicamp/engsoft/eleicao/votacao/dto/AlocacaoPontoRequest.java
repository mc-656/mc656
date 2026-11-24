package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AlocacaoPontoRequest(
        @NotNull(message = "O ID da proposta é obrigatório") UUID propostaId,
        @NotNull(message = "A quantidade de pontos é obrigatória")
                @DecimalMin(value = "0.0", message = "A pontuação não pode ser negativa")
                BigDecimal pontos) {}
