package com.unicamp.engsoft.eleicao.votacao.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaldoOrcamentoResponse(
        UUID votacaoId, BigDecimal saldoTotal, BigDecimal saldoDisponivel, boolean jaVotou) {}
