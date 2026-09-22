package com.unicamp.engsoft.eleicao.usuario.service;

import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponte entre a tabela de usuários e o Spring Security: é quem o {@code DaoAuthenticationProvider}
 * chama durante o login (RF-01).
 *
 * <p>O identificador de login é o e-mail, não o CPF.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * A mensagem é genérica de propósito. Somada ao {@code hideUserNotFoundExceptions} (ligado por
     * padrão no provider), garante que "e-mail inexistente" e "senha errada" sejam indistinguíveis
     * para quem chama — critério de aceitação da RF-01.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return usuarioRepository
                .findByEmail(email)
                .map(UsuarioAutenticado::de)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }
}
