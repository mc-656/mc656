package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Alocação de pontos de uma cédula de orçamento participativo para apuração (RF-23). */
public record AlocacaoApuravel(UUID propostaId, BigDecimal pontos) {

    public AlocacaoApuravel {
        Objects.requireNonNull(propostaId, "propostaId é obrigatório");
        Objects.requireNonNull(pontos, "pontos é obrigatório");
        if (pontos.signum() < 0) {
            throw new IllegalArgumentException("Pontos não podem ser negativos");
        }
    }
}
