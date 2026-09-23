package com.unicamp.engsoft.eleicao.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.dto.CadastroUsuarioRequest;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Regras do cadastro (RF-01), sem banco.
 *
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String SENHA = "senha-secreta-123";
    private static final String HASH = "$2a$10$hash-produzido-pelo-bcrypt";

    @Mock UsuarioRepository usuarioRepository;

    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UsuarioService usuarioService;

    private static CadastroUsuarioRequest requisicao() {
        return new CadastroUsuarioRequest("Caio", "12345678909", "caio@example.com", SENHA);
    }

    @Test
    @DisplayName("Cadastra quando não há CPF nem e-mail repetido")
    void cadastraQuandoNaoHaDuplicidade() {
        when(passwordEncoder.encode(SENHA)).thenReturn(HASH);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Usuario salvo = usuarioService.criarUsuario(requisicao());

        assertThat(salvo.getNome()).isEqualTo("Caio");
        assertThat(salvo.getEmail()).isEqualTo("caio@example.com");
    }

    /**
     * O papel não vem da requisição: o DTO não o declara e o service o fixa. É o que impede que um
     * POST crie um administrador de votação.
     */
    @Test
    @DisplayName("Todo cadastro nasce como ELEITOR")
    void cadastraSempreComoEleitor() {
        when(passwordEncoder.encode(SENHA)).thenReturn(HASH);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(usuarioService.criarUsuario(requisicao()).getPapel())
                .isEqualTo(PapelUsuario.ELEITOR);
    }

    @Test
    @DisplayName("A senha chega ao repositório já hasheada, nunca em texto plano")
    void nuncaPersisteSenhaEmTextoPlano() {
        when(passwordEncoder.encode(SENHA)).thenReturn(HASH);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        usuarioService.criarUsuario(requisicao());

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertThat(capturado.getValue().getSenhaHash()).isEqualTo(HASH).isNotEqualTo(SENHA);
    }

    @Test
    @DisplayName("E-mail já cadastrado é recusado antes de qualquer escrita")
    void recusaEmailDuplicado() {
        when(usuarioRepository.existsByEmail("caio@example.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criarUsuario(requisicao()))
                .isInstanceOf(RegraDeNegocioException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("CPF já cadastrado é recusado antes de qualquer escrita")
    void recusaCpfDuplicado() {
        when(usuarioRepository.existsByCpf("12345678909")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criarUsuario(requisicao()))
                .isInstanceOf(RegraDeNegocioException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}
