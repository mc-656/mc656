package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record DefinirPeriodoRequest(@NotNull Instant inicioEm, @NotNull Instant fimEm) {}
