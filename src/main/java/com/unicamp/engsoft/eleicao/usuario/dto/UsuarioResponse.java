package com.unicamp.engsoft.eleicao.usuario.dto;

import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Representação pública de um usuário.
 *
 * <p>Não carrega o hash da senha nem o CPF: o primeiro nunca sai do banco, o segundo não é
 * necessário em nenhuma tela que consome esta resposta.
 */
public record UsuarioResponse(
        UUID id, String nome, String email, Set<PapelUsuario> papeis, Instant criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapeis(),
                usuario.getCriadoEm());
    }
}
