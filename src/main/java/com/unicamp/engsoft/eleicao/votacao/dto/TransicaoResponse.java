package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import java.time.Instant;
import java.util.UUID;

public record TransicaoResponse(
        EstadoVotacao estadoAnterior,
        EstadoVotacao estadoNovo,
        UUID autorId,
        OrigemTransicao origem,
        String motivo,
        Instant ocorridaEm) {

    public static TransicaoResponse of(TransicaoVotacao transicao) {
        return new TransicaoResponse(
                transicao.getEstadoAnterior(),
                transicao.getEstadoNovo(),
                transicao.getAutorId(),
                transicao.getOrigem(),
                transicao.getMotivo(),
                transicao.getOcorridaEm());
    }
}
