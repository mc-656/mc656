package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.EleicaoPublica;
import java.time.LocalDate;
import java.util.UUID;

/** Eleição pública na listagem (RF-30). */
public record EleicaoPublicaResponse(
        UUID id,
        String nome,
        String tipo,
        int ano,
        LocalDate dataPrimeiroTurno,
        String abrangencia,
        FonteResponse fonte) {

    public static EleicaoPublicaResponse de(EleicaoPublica eleicao) {
        return new EleicaoPublicaResponse(
                eleicao.getId(),
                eleicao.getNome(),
                eleicao.getTipo(),
                eleicao.getAno(),
                eleicao.getDataPrimeiroTurno(),
                eleicao.getAbrangencia(),
                FonteResponse.de(eleicao.getFonte()));
    }
}
