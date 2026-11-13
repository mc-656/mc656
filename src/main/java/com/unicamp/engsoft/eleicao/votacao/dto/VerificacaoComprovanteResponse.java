package com.unicamp.engsoft.eleicao.votacao.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados atestados por um comprovante válido (RNF-01). Só existe para comprovante válido: o inválido
 * vira 400 via {@code ComprovanteInvalidoException}. Nunca inclui a opção votada (RNF-02).
 */
public record VerificacaoComprovanteResponse(UUID votacaoId, Instant emitidoEm) {}
