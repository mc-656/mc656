package com.unicamp.engsoft.eleicao.votacao.dto;

import java.time.Instant;
import java.util.UUID;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;

public record VotacaoResponse(
        UUID id,
        TipoVotacao tipo,
        Instant inicioEm,
        Instant fimEm,
        EstadoVotacao estado,
        String nome,
        String descricao) {
        
    //Pegar a votação e colocar no DTO
    public static VotacaoResponse of(Votacao votacao) {
        return new VotacaoResponse(
                votacao.getId(),
                votacao.getTipo(),
                votacao.getInicioEm(),
                votacao.getFimEm(),
                votacao.getEstado(),
                votacao.getNome(),
                votacao.getDescricao());
    }
}