package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoApuravel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Casos de borda do {@link MotorApuracao} exigidos pela RNF-05 (issue #137): empate, zero votos,
 * quórum e maioria qualificada exatamente no limite e empate no 2º lugar do segundo turno.
 *
 * <p>Os limites são verificados com aritmética exata: um percentual exibido arredondado (ex.: 2/3 =
 * 66,67%) não pode aprovar um critério que o valor real (66,666...%) não atinge.
 */
class MotorApuracaoCasosDeBordaTest {

    private MotorApuracao motor;
    private UUID candidatoA;
    private UUID candidatoB;
    private UUID candidatoC;

    @BeforeEach
    void setUp() {
        motor = new MotorApuracao();
        candidatoA = UUID.randomUUID();
        candidatoB = UUID.randomUUID();
        candidatoC = UUID.randomUUID();
    }

    private static List<VotoApuravel> votos(UUID opcao, int quantidade) {
        return Collections.nCopies(quantidade, new VotoApuravel(opcao, BigDecimal.ONE));
    }

    @SafeVarargs
    private static List<VotoApuravel> juntar(List<VotoApuravel>... grupos) {
        List<VotoApuravel> todos = new ArrayList<>();
        for (List<VotoApuravel> grupo : grupos) {
            todos.addAll(grupo);
        }
        return todos;
    }

    private static RegraVotacao simples() {
        return new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
    }

    private static RegraVotacao simplesComQuorum(String quorum) {
        return new RegraVotacao(
                TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal(quorum), false);
    }

    private static RegraVotacao qualificada(String percentual, boolean segundoTurno) {
        return new RegraVotacao(
                TipoMaioria.MAIORIA_QUALIFICADA,
                new BigDecimal(percentual),
                segundoTurno,
                null,
                false);
    }

    @Nested
    @DisplayName("Empate")
    class Empate {

        @Test
        @DisplayName("três opções com o mesmo total na maioria simples resultam em EMPATE")
        void deveDeclararEmpateTriplo() {
            // Arrange
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 2), votos(candidatoB, 2), votos(candidatoC, 2));

            // Act
            ResultadoApuracao resultado = motor.apurar(simples(), votos, 10, 6);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.vencedorId()).isNull();
            assertThat(resultado.ranking()).hasSize(3);
        }

        @Test
        @DisplayName("estratégia de desempate que não distingue as opções mantém o EMPATE")
        void deveManterEmpateQuandoEstrategiaNaoDesempata() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_SIMPLES,
                            null,
                            false,
                            null,
                            false,
                            TipoDesempate.IDADE);
            List<VotoApuravel> votos = juntar(votos(candidatoA, 3), votos(candidatoB, 3));
            EstrategiaDesempate mesmaIdade =
                    new DesempateIdade(Map.of(candidatoA, 40, candidatoB, 40));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, null, 10, 6, mesmaIdade);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.vencedorId()).isNull();
        }

        /**
         * Sorteio é o critério escolhido justamente para que o empate não trave a eleição: ao
         * contrário de idade ou nome, ele sempre distingue as opções. Sem esta verificação, um
         * sorteio que devolvesse "empate" para todo par passaria nos testes da estratégia e a
         * votação terminaria em EMPATE mesmo com o desempate configurado (mutante do PIT, #156).
         */
        @Test
        @DisplayName("desempate por sorteio resolve o empate em 1º e define um dos empatados")
        void deveDefinirVencedorPorSorteio() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_SIMPLES,
                            null,
                            false,
                            null,
                            false,
                            TipoDesempate.SORTEIO);
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 3), votos(candidatoB, 3), votos(candidatoC, 1));
            EstrategiaDesempate sorteio =
                    new DesempateAleatorio(List.of(candidatoA, candidatoB, candidatoC));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, null, 10, 7, sorteio);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.vencedorId()).isIn(candidatoA, candidatoB);
            assertThat(resultado.ranking().get(0).opcaoId()).isEqualTo(resultado.vencedorId());
            assertThat(resultado.ranking().get(2).opcaoId()).isEqualTo(candidatoC);
        }

        @Test
        @DisplayName("maioria absoluta com 1º e 2º empatados leva ambos ao segundo turno")
        void deveLevarEmpatadosEmPrimeiroAoSegundoTurno() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 2), votos(candidatoB, 2), votos(candidatoC, 1));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 5);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEGUNDO_TURNO);
            assertThat(resultado.finalistasSegundoTurno())
                    .containsExactlyInAnyOrder(candidatoA, candidatoB);
        }
    }

    @Nested
    @DisplayName("Zero votos")
    class ZeroVotos {

        @Test
        @DisplayName("lista de votos nula é tratada como vazia e resulta em SEM_VENCEDOR")
        void deveTratarListaDeVotosNulaComoVazia() {
            // Act
            ResultadoApuracao resultado = motor.apurar(simples(), null, 10, 0);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEM_VENCEDOR);
            assertThat(resultado.totalVotosValidos()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.ranking()).isEmpty();
        }

        @Test
        @DisplayName("opções zeradas na maioria absoluta resultam em EMPATE, sem segundo turno")
        void deveDeclararEmpateComZeroVotosEmMaioriaAbsoluta() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);

            // Act
            ResultadoApuracao resultado =
                    motor.apurar(regra, List.of(), List.of(candidatoA, candidatoB), 10, 0);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.finalistasSegundoTurno()).isEmpty();
            assertThat(resultado.ranking().get(0).percentualVotosValidos())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("com quórum exigido, ninguém comparecer resulta em QUORUM_NAO_ATINGIDO")
        void deveFalharQuorumComZeroParticipantes() {
            // Act
            ResultadoApuracao resultado =
                    motor.apurar(simplesComQuorum("1.00"), List.of(), List.of(candidatoA), 10, 0);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
            assertThat(resultado.percentualComparecimento()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("sem eleitores aptos o comparecimento é 0% e só passa em quórum de 0%")
        void deveTratarZeroEleitoresAptosComoComparecimentoZero() {
            // Arrange
            List<VotoApuravel> votos = votos(candidatoA, 1);

            // Act
            ResultadoApuracao reprovado = motor.apurar(simplesComQuorum("10.00"), votos, 0, 1);
            ResultadoApuracao aprovado = motor.apurar(simplesComQuorum("0"), votos, 0, 1);

            // Assert
            assertThat(reprovado.status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
            assertThat(reprovado.percentualComparecimento()).isEqualByComparingTo("0");
            assertThat(aprovado.status()).isEqualTo(StatusApuracao.DEFINIDO);
        }
    }

    @Nested
    @DisplayName("Quórum no limite")
    class QuorumNoLimite {

        @Test
        @DisplayName("comparecimento exatamente igual ao quórum é suficiente")
        void deveAtingirQuorumExatamenteNoLimite() {
            // Act
            ResultadoApuracao resultado =
                    motor.apurar(simplesComQuorum("25.00"), votos(candidatoA, 1), 4, 1);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.quorumAtingido()).isTrue();
            assertThat(resultado.percentualComparecimento()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("um participante a menos que o quórum já reprova")
        void deveReprovarQuorumUmParticipanteAbaixoDoLimite() {
            // Act
            ResultadoApuracao resultado =
                    motor.apurar(simplesComQuorum("50.00"), votos(candidatoA, 4), 10, 4);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
            assertThat(resultado.quorumAtingido()).isFalse();
        }

        @Test
        @DisplayName("arredondamento do comparecimento (2/3 = 66,67%) não aprova quórum de 66,67%")
        void naoDeveAprovarQuorumPorArredondamento() {
            // Arrange: 2 de 3 aptos = 66,666...%, exibido como 66,67%
            List<VotoApuravel> votos = votos(candidatoA, 2);

            // Act
            ResultadoApuracao acimaDoReal = motor.apurar(simplesComQuorum("66.67"), votos, 3, 2);
            ResultadoApuracao abaixoDoReal = motor.apurar(simplesComQuorum("66.66"), votos, 3, 2);

            // Assert
            assertThat(acimaDoReal.status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
            assertThat(acimaDoReal.percentualComparecimento()).isEqualByComparingTo("66.67");
            assertThat(abaixoDoReal.status()).isEqualTo(StatusApuracao.DEFINIDO);
        }
    }

    @Nested
    @DisplayName("Maioria no limite")
    class MaioriaNoLimite {

        @Test
        @DisplayName("maioria absoluta: um voto acima de 50% basta para eleger")
        void deveElegerComUmVotoAcimaDeCinquentaPorcento() {
            // Arrange: A tem 4 de 7 votos
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
            List<VotoApuravel> votos = juntar(votos(candidatoA, 4), votos(candidatoB, 3));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 7);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.vencedorId()).isEqualTo(candidatoA);
        }

        @Test
        @DisplayName("maioria qualificada: exatamente o percentual exigido (3/4 = 75%) elege")
        void deveElegerComExatamenteOPercentualQualificado() {
            // Arrange
            List<VotoApuravel> votos = juntar(votos(candidatoA, 3), votos(candidatoB, 1));

            // Act
            ResultadoApuracao resultado = motor.apurar(qualificada("75.00", true), votos, 10, 4);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.vencedorId()).isEqualTo(candidatoA);
        }

        @Test
        @DisplayName("maioria qualificada: 2/3 não atinge 66,67% mesmo exibido como 66,67%")
        void naoDeveElegerPorArredondamentoNaMaioriaQualificada() {
            // Arrange
            List<VotoApuravel> votos = juntar(votos(candidatoA, 2), votos(candidatoB, 1));

            // Act
            ResultadoApuracao resultado = motor.apurar(qualificada("66.67", false), votos, 10, 3);

            // Assert
            assertThat(resultado.ranking().get(0).percentualVotosValidos())
                    .isEqualByComparingTo("66.67");
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEM_VENCEDOR);
            assertThat(resultado.vencedorId()).isNull();
        }

        @Test
        @DisplayName("maioria qualificada: voto ponderado logo abaixo do percentual não elege")
        void naoDeveElegerComPesoLogoAbaixoDoPercentualQualificado() {
            // Arrange: A tem 5,99 de 10 (59,9%) com qualificada de 60%
            RegraVotacao regra =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_QUALIFICADA,
                            new BigDecimal("60.00"),
                            true,
                            null,
                            true);
            List<VotoApuravel> votos =
                    List.of(
                            new VotoApuravel(candidatoA, new BigDecimal("5.99")),
                            new VotoApuravel(candidatoB, new BigDecimal("4.01")));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 2);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEGUNDO_TURNO);
            assertThat(resultado.finalistasSegundoTurno()).containsExactly(candidatoA, candidatoB);
        }
    }

    @Nested
    @DisplayName("Segundo turno")
    class SegundoTurno {

        @Test
        @DisplayName("empate no 2º lugar na maioria qualificada resulta em EMPATE")
        void deveDeclararEmpateNoSegundoLugarEmMaioriaQualificada() {
            // Arrange
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 4), votos(candidatoB, 2), votos(candidatoC, 2));

            // Act
            ResultadoApuracao resultado = motor.apurar(qualificada("60.00", true), votos, 10, 8);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.finalistasSegundoTurno()).isEmpty();
        }

        @Test
        @DisplayName(
                "empate no 2º lugar resolvido pela estratégia de desempate define os finalistas")
        void deveResolverEmpateNoSegundoLugarComEstrategia() {
            // Arrange: B e C empatados em 2º; B é o mais velho
            RegraVotacao regra =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_ABSOLUTA,
                            null,
                            true,
                            null,
                            false,
                            TipoDesempate.IDADE);
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 2), votos(candidatoB, 1), votos(candidatoC, 1));
            EstrategiaDesempate idade =
                    new DesempateIdade(Map.of(candidatoA, 30, candidatoB, 70, candidatoC, 20));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, null, 10, 4, idade);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEGUNDO_TURNO);
            assertThat(resultado.finalistasSegundoTurno()).containsExactly(candidatoA, candidatoB);
        }

        @Test
        @DisplayName("empate no 2º lugar que a estratégia não resolve mantém o EMPATE")
        void deveManterEmpateNoSegundoLugarQuandoEstrategiaNaoResolve() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(
                            TipoMaioria.MAIORIA_ABSOLUTA,
                            null,
                            true,
                            null,
                            false,
                            TipoDesempate.IDADE);
            List<VotoApuravel> votos =
                    juntar(votos(candidatoA, 2), votos(candidatoB, 1), votos(candidatoC, 1));
            EstrategiaDesempate idade =
                    new DesempateIdade(Map.of(candidatoA, 30, candidatoB, 50, candidatoC, 50));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, null, 10, 4, idade);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
        }

        @Test
        @DisplayName("com exatamente duas opções, ambas vão ao segundo turno")
        void deveLevarAsDuasUnicasOpcoesAoSegundoTurno() {
            // Arrange: qualificada de 70%, A tem 6 de 10
            List<VotoApuravel> votos = juntar(votos(candidatoA, 6), votos(candidatoB, 4));

            // Act
            ResultadoApuracao resultado = motor.apurar(qualificada("70.00", true), votos, 10, 10);

            // Assert
            assertThat(resultado.status()).isEqualTo(StatusApuracao.SEGUNDO_TURNO);
            assertThat(resultado.finalistasSegundoTurno()).containsExactly(candidatoA, candidatoB);
        }
    }

    @Nested
    @DisplayName("Entradas incompletas")
    class EntradasIncompletas {

        @Test
        @DisplayName("voto ponderado sem peso conta como peso 1")
        void deveUsarPesoUmQuandoVotoPonderadoSemPeso() {
            // Arrange
            RegraVotacao regra =
                    new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, true);
            List<VotoApuravel> votos =
                    List.of(
                            new VotoApuravel(candidatoA, null),
                            new VotoApuravel(candidatoA, null),
                            new VotoApuravel(candidatoB, new BigDecimal("1.5")));

            // Act
            ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 3);

            // Assert
            assertThat(resultado.vencedorId()).isEqualTo(candidatoA);
            assertThat(resultado.ranking().get(0).totalVotos()).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("identificadores nulos em todasOpcoes são ignorados")
        void deveIgnorarOpcoesNulasCadastradas() {
            // Arrange
            List<UUID> opcoes = Arrays.asList(candidatoA, null, candidatoB);

            // Act
            ResultadoApuracao resultado =
                    motor.apurar(simples(), votos(candidatoA, 1), opcoes, 10, 1);

            // Assert
            assertThat(resultado.ranking()).hasSize(2);
            assertThat(resultado.vencedorId()).isEqualTo(candidatoA);
        }
    }
}
