package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Record que representa os parâmetros e configurações de regras de uma votação.
 *
 * @param tipoMaioria Tipo de maioria exigida (ex.: simples, qualificada).
 * @param percentualQualificado Percentual exigido para maioria qualificada
 * @param segundoTurno Indica se a votação prevê segundo turno.
 * @param quorumMinimo Quantidade mínima de participantes exigida para validar a apuração.
 * @param pesosPorEleitor Mapeamento do ID do usuário para seu respectivo peso de voto (voto
 *     ponderado).
 * @param orcamentoTotal Limite de pontos/recursos totais alocáveis para Orçamento Participativo.
 */
public record RegraVotacao(
        TipoMaioria tipoMaioria,
        BigDecimal percentualQualificado,
        boolean segundoTurno,
        Integer quorumMinimo,
        Map<UUID, BigDecimal> pesosPorEleitor,
        BigDecimal orcamentoTotal) {}
