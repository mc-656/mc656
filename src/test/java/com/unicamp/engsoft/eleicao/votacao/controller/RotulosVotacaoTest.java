package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RotulosVotacaoTest {

    private final RotulosVotacao rotulos = new RotulosVotacao();

    @Test
    void valoresNulosRetornamTextoVazio() {
        assertThat(rotulos.dataHora(null)).isEmpty();
        assertThat(rotulos.tipo(null)).isEmpty();
        assertThat(rotulos.maioria(null)).isEmpty();
        assertThat(rotulos.estado(null)).isEmpty();
    }

    @Test
    void dataHoraUtilizaFusoDeBrasilia() {
        Instant instante = Instant.parse("2026-01-15T15:30:00Z");

        assertThat(rotulos.dataHora(instante))
                .isEqualTo("15/01/2026 12:30");
    }

    @ParameterizedTest
    @EnumSource(TipoVotacao.class)
    void todosTiposPossuemRotulo(TipoVotacao tipo) {
        assertThat(rotulos.tipo(tipo)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(TipoMaioria.class)
    void todasMaioriasPossuemRotulo(TipoMaioria maioria) {
        assertThat(rotulos.maioria(maioria)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(EstadoVotacao.class)
    void todosEstadosPossuemRotulo(EstadoVotacao estado) {
        assertThat(rotulos.estado(estado)).isNotBlank();
    }
}