package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RegraVotacaoTest {

    @Test
    @DisplayName("Deve criar regra de maioria simples sem segundo turno, quórum ou pesos")
    void deveCriarMaioriaSimples() {
        // Act
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);

        // Assert
        assertEquals(TipoMaioria.MAIORIA_SIMPLES, regra.tipoMaioria());
        assertNull(regra.percentualQualificado());
        assertFalse(regra.segundoTurno());
        assertNull(regra.quorumMinimoPercentual());
        assertFalse(regra.votoPonderado());
    }

    @Test
    @DisplayName("Deve criar regra de maioria absoluta com segundo turno")
    void deveCriarMaioriaAbsolutaComSegundoTurno() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);

        assertEquals(TipoMaioria.MAIORIA_ABSOLUTA, regra.tipoMaioria());
        assertTrue(regra.segundoTurno());
    }

    @Test
    @DisplayName("Deve criar regra de maioria absoluta sem segundo turno")
    void deveCriarMaioriaAbsolutaSemSegundoTurno() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, false, null, false);

        assertFalse(regra.segundoTurno());
    }

    @Test
    @DisplayName("Deve criar regra de maioria qualificada com percentual e segundo turno")
    void deveCriarMaioriaQualificada() {
        BigDecimal doisTercos = new BigDecimal("66.67");

        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_QUALIFICADA, doisTercos, true, null, false);

        assertEquals(doisTercos, regra.percentualQualificado());
        assertTrue(regra.segundoTurno());
    }

    @ParameterizedTest
    @ValueSource(strings = {"50.01", "75", "100"})
    @DisplayName("Deve aceitar percentual qualificado acima de 50 e até 100")
    void deveAceitarPercentualQualificadoNosLimites(String percentual) {
        assertDoesNotThrow(
                () ->
                        new RegraVotacao(
                                TipoMaioria.MAIORIA_QUALIFICADA,
                                new BigDecimal(percentual),
                                false,
                                null,
                                false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "50", "100"})
    @DisplayName("Deve aceitar quórum mínimo entre 0 e 100")
    void deveAceitarQuorumNosLimites(String quorum) {
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal(quorum), false);

        assertEquals(new BigDecimal(quorum), regra.quorumMinimoPercentual());
    }

    @Test
    @DisplayName("Deve criar regra com voto ponderado")
    void deveCriarComVotoPonderado() {
        RegraVotacao regra = new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, true);

        assertTrue(regra.votoPonderado());
    }

    @Test
    @DisplayName("Deve rejeitar regra sem tipo de maioria")
    void deveRejeitarTipoMaioriaNulo() {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () -> new RegraVotacao(null, null, false, null, false));

        assertEquals("O tipo de maioria é obrigatório", ex.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar maioria qualificada sem percentual")
    void deveRejeitarQualificadaSemPercentual() {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () ->
                                new RegraVotacao(
                                        TipoMaioria.MAIORIA_QUALIFICADA, null, false, null, false));

        assertEquals("A maioria qualificada exige percentual definido", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"50", "49.99", "0", "-10", "100.01", "150"})
    @DisplayName("Deve rejeitar percentual qualificado até 50 ou acima de 100")
    void deveRejeitarPercentualQualificadoForaDoIntervalo(String percentual) {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () ->
                                new RegraVotacao(
                                        TipoMaioria.MAIORIA_QUALIFICADA,
                                        new BigDecimal(percentual),
                                        false,
                                        null,
                                        false));

        assertEquals(
                "O percentual qualificado deve ser maior que 50 e no máximo 100", ex.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = TipoMaioria.class,
            names = {"MAIORIA_SIMPLES", "MAIORIA_ABSOLUTA"})
    @DisplayName("Deve rejeitar percentual qualificado em maioria simples ou absoluta")
    void deveRejeitarPercentualForaDaQualificada(TipoMaioria tipo) {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () -> new RegraVotacao(tipo, new BigDecimal("60"), false, null, false));

        assertEquals(
                "O percentual qualificado só pode ser definido para maioria qualificada",
                ex.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar segundo turno com maioria simples")
    void deveRejeitarSegundoTurnoComMaioriaSimples() {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () ->
                                new RegraVotacao(
                                        TipoMaioria.MAIORIA_SIMPLES, null, true, null, false));

        assertEquals("O segundo turno exige maioria absoluta ou qualificada", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "100.01", "200"})
    @DisplayName("Deve rejeitar quórum mínimo fora do intervalo de 0 a 100")
    void deveRejeitarQuorumForaDoIntervalo(String quorum) {
        DadosInvalidosException ex =
                assertThrows(
                        DadosInvalidosException.class,
                        () ->
                                new RegraVotacao(
                                        TipoMaioria.MAIORIA_SIMPLES,
                                        null,
                                        false,
                                        new BigDecimal(quorum),
                                        false));

        assertEquals("O quórum mínimo deve estar entre 0 e 100", ex.getMessage());
    }
}
