package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultadoApuracaoTest {

    private UUID candidatoA;
    private UUID candidatoB;
    private Totalizacao totalizacao;

    @BeforeEach
    void setUp() {
        candidatoA = UUID.randomUUID();
        candidatoB = UUID.randomUUID();
        totalizacao =
                new Totalizacao(
                        List.of(
                                new OpcaoApurada(
                                        candidatoA, new BigDecimal("3"), new BigDecimal("60.00")),
                                new OpcaoApurada(
                                        candidatoB, new BigDecimal("2"), new BigDecimal("40.00"))),
                        new BigDecimal("5"),
                        5,
                        8,
                        new BigDecimal("62.50"));
    }

    @Test
    @DisplayName("Fábricas: devem copiar os cinco valores da totalização para o resultado")
    void deveCopiarValoresDaTotalizacao() {
        // Arrange
        List<ResultadoApuracao> resultados =
                List.of(
                        ResultadoApuracao.definido(candidatoA, totalizacao),
                        ResultadoApuracao.segundoTurno(
                                List.of(candidatoA, candidatoB), totalizacao),
                        ResultadoApuracao.empate(totalizacao),
                        ResultadoApuracao.semVencedor(totalizacao),
                        ResultadoApuracao.quorumNaoAtingido(totalizacao));

        // Act & Assert
        for (ResultadoApuracao resultado : resultados) {
            assertEquals(totalizacao.ranking(), resultado.ranking());
            assertEquals(totalizacao.totalVotosValidos(), resultado.totalVotosValidos());
            assertEquals(5, resultado.totalParticipantes());
            assertEquals(8, resultado.totalEleitoresAptos());
            assertEquals(new BigDecimal("62.50"), resultado.percentualComparecimento());
        }
    }

    @Test
    @DisplayName("definido: deve preencher o vencedor, sem finalistas e com quórum atingido")
    void deveCriarResultadoDefinido() {
        // Act
        ResultadoApuracao resultado = ResultadoApuracao.definido(candidatoA, totalizacao);

        // Assert
        assertEquals(StatusApuracao.DEFINIDO, resultado.status());
        assertEquals(candidatoA, resultado.vencedorId());
        assertTrue(resultado.finalistasSegundoTurno().isEmpty());
        assertTrue(resultado.quorumAtingido());
    }

    @Test
    @DisplayName("segundoTurno: deve preencher os finalistas, sem vencedor e com quórum atingido")
    void deveCriarResultadoSegundoTurno() {
        // Act
        ResultadoApuracao resultado =
                ResultadoApuracao.segundoTurno(List.of(candidatoA, candidatoB), totalizacao);

        // Assert
        assertEquals(StatusApuracao.SEGUNDO_TURNO, resultado.status());
        assertNull(resultado.vencedorId());
        assertEquals(List.of(candidatoA, candidatoB), resultado.finalistasSegundoTurno());
        assertTrue(resultado.quorumAtingido());
    }

    @Test
    @DisplayName("empate e semVencedor: não devem ter vencedor nem finalistas, com quórum atingido")
    void deveCriarResultadosSemDefinicao() {
        // Act
        ResultadoApuracao empate = ResultadoApuracao.empate(totalizacao);
        ResultadoApuracao semVencedor = ResultadoApuracao.semVencedor(totalizacao);

        // Assert
        assertEquals(StatusApuracao.EMPATE, empate.status());
        assertEquals(StatusApuracao.SEM_VENCEDOR, semVencedor.status());
        for (ResultadoApuracao resultado : List.of(empate, semVencedor)) {
            assertNull(resultado.vencedorId());
            assertTrue(resultado.finalistasSegundoTurno().isEmpty());
            assertTrue(resultado.quorumAtingido());
        }
    }

    @Test
    @DisplayName("quorumNaoAtingido: deve marcar o quórum como não atingido, sem vencedor")
    void deveCriarResultadoQuorumNaoAtingido() {
        // Act
        ResultadoApuracao resultado = ResultadoApuracao.quorumNaoAtingido(totalizacao);

        // Assert
        assertEquals(StatusApuracao.QUORUM_NAO_ATINGIDO, resultado.status());
        assertNull(resultado.vencedorId());
        assertTrue(resultado.finalistasSegundoTurno().isEmpty());
        assertFalse(resultado.quorumAtingido());
    }

    @Test
    @DisplayName("Totalizacao: deve guardar cópia imutável do ranking recebido")
    void deveGuardarCopiaImutavelDoRanking() {
        // Arrange
        List<OpcaoApurada> ranking = new ArrayList<>();
        ranking.add(new OpcaoApurada(candidatoA, BigDecimal.ONE, new BigDecimal("100.00")));

        // Act
        Totalizacao copia = new Totalizacao(ranking, BigDecimal.ONE, 1, 1, new BigDecimal("100"));
        ranking.add(new OpcaoApurada(candidatoB, BigDecimal.ZERO, BigDecimal.ZERO));

        // Assert
        assertEquals(1, copia.ranking().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> copia.ranking().add(new OpcaoApurada(candidatoB, null, null)));
    }

    @Test
    @DisplayName("Totalizacao: deve tratar ranking nulo como lista vazia")
    void deveTratarRankingNuloComoListaVazia() {
        // Act
        Totalizacao vazia = new Totalizacao(null, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);

        // Assert
        assertNotNull(vazia.ranking());
        assertTrue(vazia.ranking().isEmpty());
    }
}
