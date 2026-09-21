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
                "lucasmaciel",
                "Lucas Gugel Maciel",
                "l260579@dac.unicamp.br",
                "$2a$10$hashdaSenhaSegura123",
                Set.of(PapelUsuario.ELEITOR)
        );

        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        assertTrue(violations.isEmpty(), "Não deve haver violações de validação");
        assertEquals("lucasmaciel", usuario.getUsername());
        assertEquals("Lucas Gugel Maciel", usuario.getNome());
        assertEquals("l260579@dac.unicamp.br", usuario.getEmail());
        assertTrue(usuario.getPapeis().contains(PapelUsuario.ELEITOR));
    }

    @Test
    @DisplayName("Deve falhar a validação quando o email for inválido")
    // Método para testar se a validação de email esta funcionando conforme esperado
    void deveFalharQuandoEmailForInvalido() {
        Usuario usuario = new Usuario(
                "lucasmaciel",
                "Lucas Gugel Maciel",
                "email-invalido-sem-arroba",
                "$2a$10$hashdaSenhaSegura123",
                Set.of(PapelUsuario.ELEITOR)
        );

        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        assertFalse(violations.isEmpty(), "Deve conter violação devido ao email inválido");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("O email deve ser válido")));
    }
}