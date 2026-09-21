package com.unicamp.engsoft.eleicao.usuario.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario ;

class UsuarioTest {

    // Declara e instancia objeto de validação
    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Deve criar um usuário válido quando todos os dados forem informados corretamente")
    // Método para testar se o usuario criado é valido
    void deveCriarUsuarioValido() {
        Usuario usuario = new Usuario(
                "Lucas Gugel Maciel",
                "123.456.789-12",
                "l260579@dac.unicamp.br",
                "$2a$10$hashdaSenhaSegura123",
                PapelUsuario.ELEITOR
        );

        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        assertTrue(violations.isEmpty(), "Não deve haver violações de validação");
        assertEquals("Lucas Gugel Maciel", usuario.getNome());
        assertEquals("123.456.789-12", usuario.getCpf());
        assertEquals("l260579@dac.unicamp.br", usuario.getEmail());
        assertEquals(PapelUsuario.ELEITOR, usuario.getPapel());
    }

    @Test
    @DisplayName("Deve falhar a validação quando o email for inválido")
    // Método para testar se a validação de email esta funcionando conforme esperado
    void deveFalharQuandoEmailForInvalido() {
        Usuario usuario = new Usuario(
                "Lucas Gugel Maciel",
                "123.456.789-12",
                "email-invalido-sem-arroba",
                "$2a$10$hashdaSenhaSegura123",
                PapelUsuario.ELEITOR
        );

        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        assertFalse(violations.isEmpty(), "Deve conter violação devido ao email inválido");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("O email deve ser válido")));
    }
}
