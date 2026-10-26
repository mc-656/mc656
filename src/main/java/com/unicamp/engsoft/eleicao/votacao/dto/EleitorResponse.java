package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import java.math.BigDecimal;
import java.util.UUID;

/** Eleitor de uma votação com seu peso. Não expõe o CPF, assim como {@code UsuarioResponse}. */
public record EleitorResponse(UUID usuarioId, String nome, String email, BigDecimal peso) {

    public static EleitorResponse of(Usuario usuario, PapelVotacao papel) {
        return new EleitorResponse(
                usuario.getId(), usuario.getNome(), usuario.getEmail(), papel.getPeso());
    }
}
