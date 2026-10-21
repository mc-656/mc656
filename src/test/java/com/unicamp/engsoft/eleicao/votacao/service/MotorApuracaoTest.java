package com.unicamp.engsoft.eleicao.votacao.service;

import static org.junit.jupiter.api.Assertions.*;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoApuravel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotorApuracaoTest {

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

    @Test
    @DisplayName("Maioria simples: deve definir vencedor quando há opção mais votada")
    void deveElegerVencedorPorMaioriaSimples() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 3);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoA, resultado.vencedorId());
        assertTrue(resultado.finalistasSegundoTurno().isEmpty());
        assertEquals(2, resultado.ranking().size());
        assertEquals(new BigDecimal("2"), resultado.ranking().get(0).totalVotos());
        assertEquals(candidatoA, resultado.ranking().get(0).opcaoId());
        assertEquals(new BigDecimal("66.67"), resultado.ranking().get(0).percentualVotosValidos());
        assertEquals(candidatoB, resultado.ranking().get(1).opcaoId());
    }

    @Test
    @DisplayName("Maioria simples: deve declarar empate quando o 1º lugar tem votos iguais")
    void deveDeclararEmpateEmMaioriaSimples() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 2);

        assertEquals(StatusApuracao.EMPATE, resultado.status());
        assertNull(resultado.vencedorId());
    }

    @Test
    @DisplayName("Voto ponderado: deve considerar pesos de cada eleitor quando ativo")
    void deveConsiderarPesosEmVotoPonderado() {
        RegraVotacao regra = new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, true);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, new BigDecimal("1.5")),
                        new VotoApuravel(candidatoB, new BigDecimal("4.0")));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 2);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoB, resultado.vencedorId());
        assertEquals(new BigDecimal("4.0"), resultado.ranking().get(0).totalVotos());
        assertEquals(new BigDecimal("1.5"), resultado.ranking().get(1).totalVotos());
    }

    @Test
    @DisplayName("Voto ponderado desativado: deve ignorar pesos e contar peso 1 para cada voto")
    void deveIgnorarPesosQuandoVotoPonderadoDesativado() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, new BigDecimal("10.0")),
                        new VotoApuravel(candidatoB, new BigDecimal("1.0")),
                        new VotoApuravel(candidatoB, new BigDecimal("1.0")));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 3);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoB, resultado.vencedorId());
        assertEquals(new BigDecimal("2"), resultado.ranking().get(0).totalVotos());
        assertEquals(new BigDecimal("1"), resultado.ranking().get(1).totalVotos());
    }

    @Test
    @DisplayName("Maioria absoluta: deve eleger no primeiro turno se tiver mais de 50% dos válidos")
    void deveElegerEmMaioriaAbsolutaComMaisDeCinquentaPorcento() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 3);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoA, resultado.vencedorId());
    }

    @Test
    @DisplayName("Maioria absoluta: exatamente 50% não basta para eleger e leva a segundo turno")
    void deveLevarASegundoTurnoComExatamenteCinquentaPorcento() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
        // A tem 3 votos (50%), B tem 2 votos, C tem 1 voto. Total = 6.
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoC, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 6);

        assertEquals(StatusApuracao.SEGUNDO_TURNO, resultado.status());
        assertNull(resultado.vencedorId());
        assertEquals(List.of(candidatoA, candidatoB), resultado.finalistasSegundoTurno());
    }

    @Test
    @DisplayName("Maioria absoluta: 2º e 3º colocados empatados impossibilitam definir 2º turno")
    void deveDeclararEmpateQuandoSegundoETerceiroEmpatamParaSegundoTurno() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
        // A tem 2 votos, B tem 1 voto, C tem 1 voto. B e C empatados em 2º lugar.
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoC, BigDecimal.ONE));

        ResultadoApuracao resultado =
                motor.apurar(regra, votos, List.of(candidatoA, candidatoB, candidatoC), 10, 4);

        assertEquals(StatusApuracao.EMPATE, resultado.status());
        assertNull(resultado.vencedorId());
    }

    @Test
    @DisplayName("Maioria absoluta sem segundo turno: se ninguém atingir 50%, declara sem vencedor")
    void deveDeclararSemVencedorEmMaioriaAbsolutaSemSegundoTurno() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, false, null, false);
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoC, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 4);

        assertEquals(StatusApuracao.SEM_VENCEDOR, resultado.status());
        assertNull(resultado.vencedorId());
    }

    @Test
    @DisplayName("Maioria qualificada: deve eleger quando atinge o percentual configurado")
    void deveElegerEmMaioriaQualificadaQuandoAtingePercentual() {
        BigDecimal sessentaPorcento = new BigDecimal("60.00");
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_QUALIFICADA, sessentaPorcento, true, null, false);
        // A tem 3 votos de 5 (60%)
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 5);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoA, resultado.vencedorId());
    }

    @Test
    @DisplayName(
            "Maioria qualificada: deve levar a 2º turno quando não atinge percentual e há 2º turno")
    void deveLevarASegundoTurnoEmMaioriaQualificada() {
        BigDecimal setentaPorcento = new BigDecimal("70.00");
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_QUALIFICADA, setentaPorcento, true, null, false);
        // A tem 3 votos, B tem 2 votos, C tem 1 voto. Total = 6. A tem 50% (< 70%).
        List<VotoApuravel> votos =
                List.of(
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoB, BigDecimal.ONE),
                        new VotoApuravel(candidatoC, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 6);

        assertEquals(StatusApuracao.SEGUNDO_TURNO, resultado.status());
        assertEquals(List.of(candidatoA, candidatoB), resultado.finalistasSegundoTurno());
    }

    @Test
    @DisplayName("Quórum mínimo: não atingido deve resultar em QUORUM_NAO_ATINGIDO")
    void deveFalharQuandoQuorumMinimoNaoAtingido() {
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal("50.00"), false);
        List<VotoApuravel> votos = List.of(new VotoApuravel(candidatoA, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 100, 10);

        assertEquals(StatusApuracao.QUORUM_NAO_ATINGIDO, resultado.status());
        assertFalse(resultado.quorumAtingido());
        assertEquals(new BigDecimal("10.00"), resultado.percentualComparecimento());
        assertNull(resultado.vencedorId());
    }

    @Test
    @DisplayName("Quórum mínimo: atingido deve permitir a definição normal do vencedor")
    void deveProsseguirQuandoQuorumMinimoAtingido() {
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal("50.00"), false);
        List<VotoApuravel> votos = List.of(new VotoApuravel(candidatoA, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 5);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertTrue(resultado.quorumAtingido());
        assertEquals(new BigDecimal("50.00"), resultado.percentualComparecimento());
        assertEquals(candidatoA, resultado.vencedorId());
    }

    @Test
    @DisplayName(
            "Opções zeradas: opções passadas em todasOpcoes devem constar no ranking mesmo sem votos")
    void deveConterOpcoesZeradasNoRanking() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        List<VotoApuravel> votos = List.of(new VotoApuravel(candidatoA, BigDecimal.ONE));

        ResultadoApuracao resultado =
                motor.apurar(regra, votos, List.of(candidatoA, candidatoB), 10, 1);

        assertEquals(2, resultado.ranking().size());
        assertEquals(candidatoA, resultado.ranking().get(0).opcaoId());
        assertEquals(candidatoB, resultado.ranking().get(1).opcaoId());
        assertEquals(BigDecimal.ZERO, resultado.ranking().get(1).totalVotos());
    }

    @Test
    @DisplayName("Lista de votos e opções vazias deve resultar em SEM_VENCEDOR")
    void deveRetornarSemVencedorQuandoSemOpcoesEVotos() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);

        ResultadoApuracao resultado = motor.apurar(regra, List.of(), 10, 0);

        assertEquals(StatusApuracao.SEM_VENCEDOR, resultado.status());
        assertTrue(resultado.ranking().isEmpty());
    }

    @Test
    @DisplayName("Total de votos válidos zerado com opções cadastradas deve resultar em EMPATE")
    void deveRetornarEmpateQuandoTodasOpcoesTiveremZeroVotos() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);

        ResultadoApuracao resultado =
                motor.apurar(regra, List.of(), List.of(candidatoA, candidatoB), 10, 0);

        assertEquals(StatusApuracao.EMPATE, resultado.status());
        assertEquals(BigDecimal.ZERO, resultado.totalVotosValidos());
    }

    @Test
    @DisplayName("Votos nulos na lista devem ser ignorados")
    void deveIgnorarVotosNulos() {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        List<VotoApuravel> votos =
                java.util.Arrays.asList(
                        null,
                        new VotoApuravel(null, BigDecimal.ONE),
                        new VotoApuravel(candidatoA, BigDecimal.ONE));

        ResultadoApuracao resultado = motor.apurar(regra, votos, 10, 1);

        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoA, resultado.vencedorId());
    }

    @Test
    @DisplayName("Deve rejeitar regra nula")
    void deveRejeitarRegraNula() {
        assertThrows(IllegalArgumentException.class, () -> motor.apurar(null, List.of(), 10, 5));
    }
}
