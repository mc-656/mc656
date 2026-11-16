package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import java.math.BigDecimal;
import java.util.UUID;

public record PropostaResponse(UUID id, String titulo, String descricao, BigDecimal custo) {

    public static PropostaResponse of(Proposta proposta) {
        return new PropostaResponse(
                proposta.getId(),
                proposta.getTitulo(),
                proposta.getDescricao(),
                proposta.getCusto());
    }
}
