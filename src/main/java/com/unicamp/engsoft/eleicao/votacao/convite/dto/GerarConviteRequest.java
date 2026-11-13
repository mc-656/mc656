package com.unicamp.engsoft.eleicao.votacao.convite.dto;

import com.unicamp.engsoft.eleicao.votacao.convite.domain.ModoConvite;
import jakarta.validation.constraints.NotNull;

/** Modo de acesso do convite (RF-17). Chamar de novo só troca o modo; o link continua o mesmo. */
public record GerarConviteRequest(
        @NotNull(message = "O modo do convite é obrigatório") ModoConvite modo) {}
