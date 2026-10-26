package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Pedido de transição de estado.
 *
 * @param novoFimEm opcional; só aceito ao retomar uma votação {@code Suspensa} ({@code Suspensa →
 *     EmVotacao}), para prorrogar a data de fim (#55).
 */
public record TransicaoRequest(
        @NotNull EstadoVotacao destino, @Size(max = 500) String motivo, Instant novoFimEm) {}
