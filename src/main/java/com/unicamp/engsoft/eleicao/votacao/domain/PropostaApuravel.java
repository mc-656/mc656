package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Proposta elegível recebida pelo motor de apuração de orçamento participativo (RF-23). */
public record PropostaApuravel(UUID id, String titulo, BigDecimal custo, Instant criadoEm) {

    public PropostaApuravel {
        Objects.requireNonNull(id, "id da proposta é obrigatório");
        Objects.requireNonNull(titulo, "título da proposta é obrigatório");
        Objects.requireNonNull(custo, "custo da proposta é obrigatório");
        if (custo.signum() <= 0) {
            throw new IllegalArgumentException("O custo da proposta deve ser positivo");
        }
        Objects.requireNonNull(criadoEm, "data de criação da proposta é obrigatória");
    }
}
