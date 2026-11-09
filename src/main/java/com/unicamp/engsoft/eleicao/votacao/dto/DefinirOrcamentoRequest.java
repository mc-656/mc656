package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Definição do orçamento total do orçamento participativo (RF-20). */
public record DefinirOrcamentoRequest(
        @NotNull
                @Positive(message = "O orçamento total deve ser maior que zero")
                @Digits(integer = 13, fraction = 2)
                BigDecimal orcamentoTotal) {}
