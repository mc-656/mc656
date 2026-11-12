package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Cadastro ou edição de proposta (RF-20). Os limites espelham as colunas da V14. */
public record PropostaRequest(
        @NotBlank @Size(max = 100) String titulo,
        @NotBlank @Size(max = 500) String descricao,
        @NotNull
                @Positive(message = "O custo da proposta deve ser maior que zero")
                @Digits(integer = 13, fraction = 2)
                BigDecimal custo) {}
