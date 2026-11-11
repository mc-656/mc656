package com.unicamp.engsoft.eleicao.votacao.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Visão do orçamento participativo: orçamento total (nulo se ainda não definido), propostas em
 * ordem de cadastro e avisos ao organizador (RF-20).
 */
public record PropostasResponse(
        BigDecimal orcamentoTotal, List<PropostaResponse> propostas, List<String> avisos) {}
