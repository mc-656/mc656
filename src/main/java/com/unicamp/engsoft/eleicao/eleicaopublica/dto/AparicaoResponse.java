package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.AparicaoCandidato;
import java.time.Instant;
import java.util.UUID;

/** Item da agenda futura de aparições públicas de um candidato (RF-31, #132). */
public record AparicaoResponse(
        UUID id,
        String tipo,
        String titulo,
        Instant dataHora,
        String local,
        String descricao,
        FonteResponse fonte) {

    public static AparicaoResponse de(AparicaoCandidato aparicao) {
        return new AparicaoResponse(
                aparicao.getId(),
                aparicao.getTipo(),
                aparicao.getTitulo(),
                aparicao.getDataHora(),
                aparicao.getLocal(),
                aparicao.getDescricao(),
                FonteResponse.de(aparicao.getFonte()));
    }
}
