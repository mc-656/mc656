package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        assertThat(rotulos.dataHora(instante)).isEqualTo("15/01/2026 12:30");
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
    @DisplayName("Deve retornar vazio quando a data for nula")
    void deveRetornarVazioParaDataNula() {
        assertThat(rotulos.dataHora(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve formatar data e hora no fuso de Brasília")
    void deveFormatarDataHoraNoFusoDeBrasilia() {
        Instant instante = Instant.parse("2026-11-13T15:30:00Z");

        assertThat(rotulos.dataHora(instante)).isEqualTo("13/11/2026 12:30");

        assertThat(RotulosVotacao.FUSO).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    @DisplayName("Deve retornar vazio para tipo de votação nulo")
    void deveRetornarVazioParaTipoNulo() {
        assertThat(rotulos.tipo(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar os rótulos de todos os tipos de votação")
    void deveRetornarRotulosDosTiposDeVotacao() {
        assertThat(rotulos.tipo(TipoVotacao.ELEICAO_PRIVADA)).isEqualTo("Eleição privada");

        assertThat(rotulos.tipo(TipoVotacao.ORCAMENTO_PARTICIPATIVO))
                .isEqualTo("Orçamento participativo");
    }

    @Test
    @DisplayName("Deve retornar vazio para maioria nula")
    void deveRetornarVazioParaMaioriaNula() {
        assertThat(rotulos.maioria(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar os rótulos de todos os tipos de maioria")
    void deveRetornarRotulosDosTiposDeMaioria() {
        assertThat(rotulos.maioria(TipoMaioria.MAIORIA_SIMPLES)).isEqualTo("Maioria simples");

        assertThat(rotulos.maioria(TipoMaioria.MAIORIA_ABSOLUTA)).isEqualTo("Maioria absoluta");

        assertThat(rotulos.maioria(TipoMaioria.MAIORIA_QUALIFICADA))
                .isEqualTo("Maioria qualificada");
    }

    @Test
    @DisplayName("Deve retornar vazio para estado nulo")
    void deveRetornarVazioParaEstadoNulo() {
        assertThat(rotulos.estado(null)).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar os rótulos de todos os estados da votação")
    void deveRetornarRotulosDosEstados() {
        assertThat(rotulos.estado(EstadoVotacao.RascunhoVotacao)).isEqualTo("Rascunho");

        assertThat(rotulos.estado(EstadoVotacao.Publicada)).isEqualTo("Publicada");

        assertThat(rotulos.estado(EstadoVotacao.EmVotacao)).isEqualTo("Em votação");

        assertThat(rotulos.estado(EstadoVotacao.Suspensa)).isEqualTo("Suspensa");

        assertThat(rotulos.estado(EstadoVotacao.Apuracao)).isEqualTo("Em apuração");

        assertThat(rotulos.estado(EstadoVotacao.Encerrada)).isEqualTo("Encerrada");

        assertThat(rotulos.estado(EstadoVotacao.Homologada)).isEqualTo("Homologada");

        assertThat(rotulos.estado(EstadoVotacao.Anulada)).isEqualTo("Anulada");

        assertThat(rotulos.estado(EstadoVotacao.Cancelada)).isEqualTo("Cancelada");
    }
}
