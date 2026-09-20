package com.unicamp.engsoft.eleicao.usuario.service;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;    

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuarioValido;

    @BeforeEach
    void setUp() {
        usuarioValido = new Usuario(
                "João Silva",
                "12345678909",
                "joao@email.com",
                "senhaSegura123",
                PapelUsuario.ELEITOR
        );
    }
    
    @Test
    void deveCriarUsuarioComSucesso() {
        when(usuarioRepository.existsByCpf(usuarioValido.getCpf())).thenReturn(false);
        when(usuarioRepository.existsByEmail(usuarioValido.getEmail())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioValido);
    }
    @Test
    void deveLancarExceptionQuandoCpfJaEstiverCadastrado() {
        when(usuarioRepository.existsByCpf(usuarioValido.getCpf())).thenReturn(true);

        RegraDeNegocioException exception = assertThrows(RegraDeNegocioException.class, () -> {
            usuarioService.cadastrar(usuarioValido);
        });

        assertNotNull(exception);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
    @Test
    void deveLancarExceptionQuandoEmailJaEstiverCadastrado() {
        when(usuarioRepository.existsByCpf(usuarioValido.getCpf())).thenReturn(false);
        when(usuarioRepository.existsByEmail(usuarioValido.getEmail())).thenReturn(true);

        RegraDeNegocioException exception = assertThrows(RegraDeNegocioException.class, () -> {
            usuarioService.cadastrar(usuarioValido);
        });

        assertNotNull(exception);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
    @Test
    void deveLancarExceptionQuandoNomeForNulo() {
        when(usuarioRepository.existsByCpf(usuarioValido.getCpf())).thenReturn(false);
        when(usuarioRepository.existsByEmail(usuarioValido.getEmail())).thenReturn(false);

        RegraDeNegocioException exception = assertThrows(RegraDeNegocioException.class, () -> {
            usuarioService.cadastrar(usuarioValido);
        });

        assertNotNull(exception);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}
