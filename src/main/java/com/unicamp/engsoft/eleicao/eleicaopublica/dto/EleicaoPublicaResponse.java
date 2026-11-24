package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.EleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.SituacaoEleicaoPublica;
import java.time.LocalDate;
import java.util.UUID;

/** Eleição pública na listagem (RF-30). */
public record EleicaoPublicaResponse(
        UUID id,
        String nome,
        String tipo,
        int ano,
        LocalDate dataPrimeiroTurno,
        LocalDate dataFim,
        SituacaoEleicaoPublica situacao,
        String abrangencia,
        FonteResponse fonte) {

    public static EleicaoPublicaResponse de(EleicaoPublica eleicao, LocalDate hoje) {
        return new EleicaoPublicaResponse(
                eleicao.getId(),
                eleicao.getNome(),
                eleicao.getTipo(),
                eleicao.getAno(),
                eleicao.getDataPrimeiroTurno(),
                eleicao.getDataFim(),
                eleicao.situacaoEm(hoje),
                eleicao.getAbrangencia(),
                FonteResponse.de(eleicao.getFonte()));
    }

    public static EleicaoPublicaResponse de(EleicaoPublica eleicao) {
        return de(eleicao, LocalDate.now());
    }
}
