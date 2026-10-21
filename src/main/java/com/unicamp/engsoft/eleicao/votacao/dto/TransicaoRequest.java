package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransicaoRequest(@NotNull EstadoVotacao destino, @Size(max = 500) String motivo) {}
