package com.unicamp.engsoft.eleicao.votacao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.repository.CrudRepository;

/**
 * Participação e cédula são append-only (SPECS §7.3): os repositórios não oferecem nenhuma operação
 * de alteração ou remoção, nem herdada.
 */
class VotoAppendOnlyTest {

    @ParameterizedTest
    @ValueSource(classes = {ParticipacaoRepository.class, CedulaRepository.class})
    @DisplayName("Repositório de voto não herda CRUD nem declara update/delete")
    void repositorioSemUpdateNemDelete(Class<?> repositorio) {
        // Act
        String[] metodos =
                Arrays.stream(repositorio.getMethods()).map(Method::getName).toArray(String[]::new);

        // Assert
        assertThat(CrudRepository.class.isAssignableFrom(repositorio)).isFalse();
        assertThat(metodos)
                .noneMatch(nome -> nome.startsWith("delete"))
                .noneMatch(nome -> nome.startsWith("remove"))
                .noneMatch(nome -> nome.startsWith("update"))
                .anyMatch(nome -> nome.startsWith("save"));
    }
}
