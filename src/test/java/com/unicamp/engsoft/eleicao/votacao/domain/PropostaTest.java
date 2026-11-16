package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Invariantes de proposta e orçamento total que não dependem do banco (RF-20). */
class PropostaTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-100"})
    @DisplayName("Proposta com custo zero ou negativo é rejeitada")
    void rejeitaCustoNaoPositivo(String custo) {
        // Act + Assert
        assertThatThrownBy(
                        () ->
                                new Proposta(
                                        UUID.randomUUID(),
                                        "Praça",
                                        "Nova praça",
                                        new BigDecimal(custo)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    @DisplayName("Proposta guarda título, descrição e custo")
    void criaPropostaValida() {
        // Act
        Proposta proposta =
                new Proposta(UUID.randomUUID(), "Praça", "Nova praça", new BigDecimal("10.50"));

        // Assert
        assertThat(proposta.getTitulo()).isEqualTo("Praça");
        assertThat(proposta.getDescricao()).isEqualTo("Nova praça");
        assertThat(proposta.getCusto()).isEqualByComparingTo("10.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("Orçamento total zero ou negativo é rejeitado")
    void rejeitaOrcamentoNaoPositivo(String valor) {
        // Arrange
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.parse("2099-10-01T12:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Orçamento",
                        "Descrição");

        // Act + Assert
        assertThatThrownBy(() -> votacao.definirOrcamentoTotal(new BigDecimal(valor)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("maior que zero");
        assertThat(votacao.getOrcamentoTotal()).isNull();
    }
}
