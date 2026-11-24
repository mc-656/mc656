package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Resumo apurado de uma proposta do orçamento participativo (RF-23). */
public record PropostaApurada(
        UUID propostaId, BigDecimal totalPontos, BigDecimal custo, StatusPropostaApurada status) {}
