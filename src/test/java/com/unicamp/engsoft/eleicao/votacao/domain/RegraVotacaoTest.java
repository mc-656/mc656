package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Particionamento em classes de equivalência e análise de valor limite do construtor de {@link
 * RegraVotacao} (RF-11, #150).
 *
 * <p>Entradas: {@code tipoMaioria}, {@code percentualQualificado} (depende do tipo), {@code
 * segundoTurno} (depende do tipo), {@code quorumMinimoPercentual} e {@code tipoDesempate}. Cada
 * teste traz no {@code @DisplayName} o ID da classe que exercita:
 *
 * <ul>
 *   <li>CE-V1: maioria simples ou absoluta, sem percentual;
 *   <li>CE-V2: maioria qualificada com percentual em (50, 100];
 *   <li>CE-V3: segundo turno com maioria absoluta ou qualificada;
 *   <li>CE-V4: quórum nulo (votação sem quórum);
 *   <li>CE-V5: quórum em [0, 100];
 *   <li>CE-V6: desempate nulo, que vira {@code NENHUM};
 *   <li>CE-I1: tipo de maioria nulo;
 *   <li>CE-I2: percentual presente em maioria simples ou absoluta;
 *   <li>CE-I3: maioria qualificada sem percentual;
 *   <li>CE-I4: percentual qualificado até 50;
 *   <li>CE-I5: percentual qualificado acima de 100;
 *   <li>CE-I6: segundo turno com maioria simples;
 *   <li>CE-I7: quórum negativo;
 *   <li>CE-I8: quórum acima de 100.
 * </ul>
 *
 * <p>Os limites ficam em {@link ValorLimite}. As colunas são {@code NUMERIC} sem escala, então não
 * existe "o menor valor acima de 50": além do centésimo usado na interface, o teste usa uma casa a
 * mais e o mesmo número com escala diferente ({@code 50.00}), que separa {@code compareTo} de
 * {@code equals} em {@link BigDecimal}.
 */
class RegraVotacaoTest {

    private static final String MSG_PERCENTUAL =
            "O percentual qualificado deve ser maior que 50 e no máximo 100";
    private static final String MSG_QUORUM = "O quórum mínimo deve estar entre 0 e 100";

    private static RegraVotacao qualificada(String percentual) {
        return new RegraVotacao(
                TipoMaioria.MAIORIA_QUALIFICADA, new BigDecimal(percentual), false, null, false);
    }

    private static RegraVotacao comQuorum(String quorum) {
        return new RegraVotacao(
                TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal(quorum), false);
    }

    @Nested
    @DisplayName("Classes válidas")
    class ClassesValidas {

        @ParameterizedTest
        @EnumSource(
                value = TipoMaioria.class,
                names = {"MAIORIA_SIMPLES", "MAIORIA_ABSOLUTA"})
        @DisplayName("CE-V1: maioria simples ou absoluta sem percentual é aceita")
        void maioriaSemPercentual(TipoMaioria tipo) {
            // Act
            RegraVotacao regra = new RegraVotacao(tipo, null, false, null, false);

            // Assert
            assertEquals(tipo, regra.tipoMaioria());
            assertNull(regra.percentualQualificado());
            assertFalse(regra.segundoTurno());
            assertNull(regra.quorumMinimoPercentual());
            assertFalse(regra.votoPonderado());
        }

        @Test
        @DisplayName("CE-V2: maioria qualificada com percentual dentro do intervalo é aceita")
        void maioriaQualificada() {
            // Act
            RegraVotacao regra = qualificada("66.67");

            // Assert
            assertEquals(TipoMaioria.MAIORIA_QUALIFICADA, regra.tipoMaioria());
            assertEquals(new BigDecimal("66.67"), regra.percentualQualificado());
        }

        @ParameterizedTest
        @EnumSource(
                value = TipoMaioria.class,
                names = {"MAIORIA_ABSOLUTA", "MAIORIA_QUALIFICADA"})
        @DisplayName("CE-V3: segundo turno com maioria absoluta ou qualificada é aceito")
        void segundoTurnoPermitido(TipoMaioria tipo) {
            // Arrange
            BigDecimal percentual =
                    tipo == TipoMaioria.MAIORIA_QUALIFICADA ? new BigDecimal("60") : null;

            // Act
            RegraVotacao regra = new RegraVotacao(tipo, percentual, true, null, false);

            // Assert
            assertTrue(regra.segundoTurno());
        }

        @Test
        @DisplayName("CE-V4: quórum nulo indica votação sem quórum")
        void semQuorum() {
            // Act
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);

            // Assert
            assertNull(regra.quorumMinimoPercentual());
        }

        @Test
        @DisplayName("CE-V5: quórum dentro de 0 a 100 é aceito")
        void quorumNoIntervalo() {
            // Act
            RegraVotacao regra = comQuorum("50");

            // Assert
            assertEquals(new BigDecimal("50"), regra.quorumMinimoPercentual());
        }

        @Test
        @DisplayName("CE-V6: desempate nulo assume NENHUM; o informado é mantido")
        void desempatePadrao() {
            // Act
            RegraVotacao semDesempate =
                    new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false, null);
            RegraVotacao comSorteio =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_SIMPLES,
                            null,
                            false,
                            null,
                            false,
                            TipoDesempate.SORTEIO);

            // Assert
            assertEquals(TipoDesempate.NENHUM, semDesempate.tipoDesempate());
            assertEquals(TipoDesempate.SORTEIO, comSorteio.tipoDesempate());
        }

        @Test
        @DisplayName("Voto ponderado é aceito com qualquer tipo de maioria")
        void votoPonderado() {
            // Act
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, true);

            // Assert
            assertTrue(regra.votoPonderado());
        }
    }

    @Nested
    @DisplayName("Classes inválidas")
    class ClassesInvalidas {

        @Test
        @DisplayName("CE-I1: regra sem tipo de maioria é rejeitada")
        void tipoMaioriaNulo() {
            // Act
            DadosInvalidosException ex =
                    assertThrows(
                            DadosInvalidosException.class,
                            () -> new RegraVotacao(null, null, false, null, false));

            // Assert
            assertEquals("O tipo de maioria é obrigatório", ex.getMessage());
        }

        @ParameterizedTest
        @EnumSource(
                value = TipoMaioria.class,
                names = {"MAIORIA_SIMPLES", "MAIORIA_ABSOLUTA"})
        @DisplayName("CE-I2: percentual qualificado em maioria simples ou absoluta é rejeitado")
        void percentualForaDaQualificada(TipoMaioria tipo) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(
                            DadosInvalidosException.class,
                            () -> new RegraVotacao(tipo, new BigDecimal("60"), false, null, false));

            // Assert
            assertEquals(
                    "O percentual qualificado só pode ser definido para maioria qualificada",
                    ex.getMessage());
        }

        @Test
        @DisplayName("CE-I3: maioria qualificada sem percentual é rejeitada")
        void qualificadaSemPercentual() {
            // Act
            DadosInvalidosException ex =
                    assertThrows(
                            DadosInvalidosException.class,
                            () ->
                                    new RegraVotacao(
                                            TipoMaioria.MAIORIA_QUALIFICADA,
                                            null,
                                            false,
                                            null,
                                            false));

            // Assert
            assertEquals("A maioria qualificada exige percentual definido", ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"30", "0", "-10"})
        @DisplayName("CE-I4: percentual qualificado até 50 é rejeitado")
        void percentualAteCinquenta(String percentual) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> qualificada(percentual));

            // Assert
            assertEquals(MSG_PERCENTUAL, ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"150", "1000"})
        @DisplayName("CE-I5: percentual qualificado acima de 100 é rejeitado")
        void percentualAcimaDeCem(String percentual) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> qualificada(percentual));

            // Assert
            assertEquals(MSG_PERCENTUAL, ex.getMessage());
        }

        @Test
        @DisplayName("CE-I6: segundo turno com maioria simples é rejeitado")
        void segundoTurnoComMaioriaSimples() {
            // Act
            DadosInvalidosException ex =
                    assertThrows(
                            DadosInvalidosException.class,
                            () ->
                                    new RegraVotacao(
                                            TipoMaioria.MAIORIA_SIMPLES, null, true, null, false));

            // Assert
            assertEquals("O segundo turno exige maioria absoluta ou qualificada", ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "-50"})
        @DisplayName("CE-I7: quórum negativo é rejeitado")
        void quorumNegativo(String quorum) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> comQuorum(quorum));

            // Assert
            assertEquals(MSG_QUORUM, ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"150", "200"})
        @DisplayName("CE-I8: quórum acima de 100 é rejeitado")
        void quorumAcimaDeCem(String quorum) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> comQuorum(quorum));

            // Assert
            assertEquals(MSG_QUORUM, ex.getMessage());
        }
    }

    /**
     * Valores em torno dos limites dos dois percentuais. O qualificado é aberto em 50 e fechado em
     * 100; o quórum é fechado nos dois lados. Cada valor é um caso separado no relatório.
     */
    @Nested
    @DisplayName("Análise de valor limite")
    class ValorLimite {

        @ParameterizedTest(name = "percentual {0} é aceito")
        @ValueSource(strings = {"50.0001", "50.01", "99.99", "100", "100.00"})
        @DisplayName("AVL percentual qualificado: valores logo acima de 50 e até 100 são aceitos")
        void percentualQualificadoAceito(String percentual) {
            // Act
            RegraVotacao regra = qualificada(percentual);

            // Assert
            assertEquals(new BigDecimal(percentual), regra.percentualQualificado());
        }

        @ParameterizedTest(name = "percentual {0} é rejeitado")
        @ValueSource(strings = {"49.99", "50", "50.00", "100.0001", "100.01"})
        @DisplayName(
                "AVL percentual qualificado: 50 exato e valores logo acima de 100 são rejeitados")
        void percentualQualificadoRejeitado(String percentual) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> qualificada(percentual));

            // Assert
            assertEquals(MSG_PERCENTUAL, ex.getMessage());
        }

        @ParameterizedTest(name = "quórum {0} é aceito")
        @ValueSource(strings = {"0", "0.00", "0.01", "99.99", "100", "100.00"})
        @DisplayName("AVL quórum: 0 e 100 entram no intervalo, assim como os vizinhos internos")
        void quorumAceito(String quorum) {
            // Act
            RegraVotacao regra = comQuorum(quorum);

            // Assert
            assertEquals(new BigDecimal(quorum), regra.quorumMinimoPercentual());
        }

        @ParameterizedTest(name = "quórum {0} é rejeitado")
        @ValueSource(strings = {"-0.0001", "-0.01", "100.0001", "100.01"})
        @DisplayName("AVL quórum: valores logo abaixo de 0 e logo acima de 100 são rejeitados")
        void quorumRejeitado(String quorum) {
            // Act
            DadosInvalidosException ex =
                    assertThrows(DadosInvalidosException.class, () -> comQuorum(quorum));

            // Assert
            assertEquals(MSG_QUORUM, ex.getMessage());
        }
    }
}
