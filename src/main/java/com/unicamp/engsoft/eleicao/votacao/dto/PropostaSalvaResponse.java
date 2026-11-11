package com.unicamp.engsoft.eleicao.votacao.dto;

import java.util.List;

/**
 * Resposta do cadastro/edição de proposta: a proposta salva mais os avisos ao organizador (ex.:
 * mais de 10 propostas, RF-20). Aviso não é erro — a operação foi concluída.
 */
public record PropostaSalvaResponse(PropostaResponse proposta, List<String> avisos) {}
