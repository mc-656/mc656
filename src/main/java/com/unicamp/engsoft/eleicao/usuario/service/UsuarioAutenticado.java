package com.unicamp.engsoft.eleicao.usuario.service;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapta {@link Usuario} ao contrato do Spring Security.
 *
 * <p>Carrega o {@code id} além do e-mail porque é ele que vai no {@code sub} do JWT: o e-mail pode
 * mudar, o identificador não. Também evita uma segunda consulta ao banco na emissão do token.
 */
public final class UsuarioAutenticado implements UserDetails {

    private final UUID id;
    private final String email;
    private final String senhaHash;
    private final List<GrantedAuthority> autoridades;

    private UsuarioAutenticado(
            UUID id, String email, String senhaHash, List<GrantedAuthority> autoridades) {
        this.id = id;
        this.email = email;
        this.senhaHash = senhaHash;
        this.autoridades = autoridades;
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        List<GrantedAuthority> autoridades =
                usuario.getPapeis().stream()
                        .map(
                                papel ->
                                        (GrantedAuthority)
                                                new SimpleGrantedAuthority("ROLE_" + papel.name()))
                        .toList();
        return new UsuarioAutenticado(
                usuario.getId(), usuario.getEmail(), usuario.getSenhaHash(), autoridades);
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return autoridades;
    }

    /** O Spring Security compara este valor com a senha enviada; aqui é sempre o hash BCrypt. */
    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
