package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.Fonte;
import java.time.Instant;

/** Fonte e data de atualização que acompanham cada informação exibida (RF-31/RF-33). */
public record FonteResponse(String nome, String url, Instant atualizadoEm) {

    public static FonteResponse de(Fonte fonte) {
        return new FonteResponse(fonte.nome(), fonte.url(), fonte.atualizadoEm());
    }
}
