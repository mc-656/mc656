package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record VotoOrcamentoRequest(
        @NotEmpty(message = "É necessário informar ao menos uma alocação de pontos")
                List<@Valid AlocacaoPontoRequest> alocacoes) {}
