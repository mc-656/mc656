package com.unicamp.engsoft.eleicao.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Ponte entre o repositório e o contrato do Spring Security. */
@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock UsuarioRepository usuarioRepository;

    @InjectMocks UsuarioDetailsService usuarioDetailsService;

    private static Usuario usuario() {
        Usuario usuario = new Usuario("Caio", "12345678909", "caio@example.com", "$2a$10$hash");
        usuario.setId(UUID.randomUUID());
        return usuario;
    }

    @Test
    @DisplayName("Carrega o usuário sem autoridades globais: papel só existe por votação")
    void carregaUsuarioSemAutoridades() {
        when(usuarioRepository.findByEmail("caio@example.com")).thenReturn(Optional.of(usuario()));

        UserDetails detalhes = usuarioDetailsService.loadUserByUsername("caio@example.com");

        assertThat(detalhes.getAuthorities()).isEmpty();
        assertThat(detalhes.getUsername()).isEqualTo("caio@example.com");
        assertThat(detalhes.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    @DisplayName("E-mail desconhecido vira UsernameNotFoundException sem revelar o motivo")
    void recusaEmailDesconhecido() {
        when(usuarioRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioDetailsService.loadUserByUsername("ninguem@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciais inválidas");
    }
}
