package com.unicamp.engsoft.eleicao.votacao.dto;

import java.time.Instant;

import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarVotacaoRequest(
        @NotNull TipoVotacao tipo,
        @NotNull Instant inicioEm,
        @NotNull Instant fimEm,
        @NotBlank @Size(max = 50) String nome,
        @NotBlank @Size(max = 100) String descricao) {

    //transformar o DTO em votação 
    public Votacao paraVotacao() {
        return new Votacao(tipo, inicioEm, fimEm, nome, descricao);
    }
}