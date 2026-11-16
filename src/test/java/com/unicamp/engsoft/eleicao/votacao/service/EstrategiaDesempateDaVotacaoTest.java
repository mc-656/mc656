package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.*;

import com.unicamp.engsoft.eleicao.votacao.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class EstrategiaDesempateDaVotacaoTest {
    private final EstrategiaDesempateDaVotacao factory = new EstrategiaDesempateDaVotacao();

    private Votacao votacao(TipoDesempate tipo) {
        Votacao v =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        "Nome",
                        "Descrição");
        v.configurarRegras(
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false, tipo));
        return v;
    }

    private CandidatoOuChapa opcao(String nome, LocalDate nascimento) {
        var o = new CandidatoOuChapa(UUID.randomUUID(), nome, null, nascimento);
        ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
        return o;
    }

    @ParameterizedTest
    @EnumSource(TipoDesempate.class)
    void selecionaEstrategiaConfigurada(TipoDesempate tipo) {
        var a = opcao("Ana", LocalDate.of(1980, 1, 1));
        var b = opcao("Bia", LocalDate.of(1980, 1, 2));
        var estrategia = factory.criar(votacao(tipo), List.of(a, b));
        if (tipo == TipoDesempate.NENHUM) {
            assertThat(estrategia).isNull();
        } else {
            assertThat(estrategia).isNotNull();
            if (tipo != TipoDesempate.SORTEIO) {
                assertThat(
                                estrategia.compare(
                                        new OpcaoApurada(a.getId(), BigDecimal.ONE, BigDecimal.TEN),
                                        new OpcaoApurada(
                                                b.getId(), BigDecimal.ONE, BigDecimal.TEN)))
                        .isNegative();
            }
        }
    }

    @Test
    void idadeNaoPodeSerInferidaSemNascimento() {
        assertThatThrownBy(
                        () ->
                                factory.criar(
                                        votacao(TipoDesempate.IDADE), List.of(opcao("Ana", null))))
                .hasMessageContaining("data de nascimento válida");
        assertThatThrownBy(
                        () ->
                                factory.criar(
                                        votacao(TipoDesempate.IDADE),
                                        List.of(opcao("Ana", LocalDate.of(2099, 1, 1)))))
                .hasMessageContaining("data de nascimento válida");
    }
}
