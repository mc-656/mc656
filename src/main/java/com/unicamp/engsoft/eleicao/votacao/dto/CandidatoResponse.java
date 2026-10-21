package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import java.util.UUID;

public record CandidatoResponse(UUID id, String nome, String descricao) {

    public static CandidatoResponse of(CandidatoOuChapa candidato) {
        return new CandidatoResponse(
                candidato.getId(), candidato.getNome(), candidato.getDescricao());
    }
}
