package com.unicamp.engsoft.eleicao.votacao.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;

/** Formulário de definição de período em votações criadas como segundo turno (#50, #52). */
public record DefinirPeriodoForm(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicioEm,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fimEm) {

    public static DefinirPeriodoForm vazio() {
        return new DefinirPeriodoForm(null, null);
    }

    public DefinirPeriodoRequest paraRequest(ZoneId fuso) {
        return new DefinirPeriodoRequest(
                inicioEm == null ? null : inicioEm.atZone(fuso).toInstant(),
                fimEm == null ? null : fimEm.atZone(fuso).toInstant());
    }
}
