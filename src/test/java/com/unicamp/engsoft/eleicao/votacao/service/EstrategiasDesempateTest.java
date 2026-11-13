package com.unicamp.engsoft.eleicao.votacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unicamp.engsoft.eleicao.votacao.domain.OpcaoApurada;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstrategiasDesempateTest {

    private static OpcaoApurada opcao(UUID id) {
        return new OpcaoApurada(id, BigDecimal.TEN, BigDecimal.TEN);
    }

    @Test
    @DisplayName("Desempate por idade: o candidato mais velho fica à frente")
    void desempateIdade_devePriorizarCandidatoMaisVelho() {
        // Arrange
        UUID idMaisVelho = UUID.randomUUID();
        UUID idMaisNovo = UUID.randomUUID();
        DesempateIdade estrategia = new DesempateIdade(Map.of(idMaisVelho, 55, idMaisNovo, 30));
        OpcaoApurada opMaisVelho = opcao(idMaisVelho);
        OpcaoApurada opMaisNovo = opcao(idMaisNovo);

        // Act & Assert: negativo significa que o primeiro parâmetro ganha
        assertTrue(estrategia.compare(opMaisVelho, opMaisNovo) < 0, "O mais velho deve vencer");
        assertTrue(estrategia.compare(opMaisNovo, opMaisVelho) > 0, "O mais novo deve perder");
        assertEquals(0, estrategia.compare(opMaisVelho, opMaisVelho), "Mesma idade deve empatar");
    }

    @Test
    @DisplayName(
            "Desempate por idade: opção sem idade cadastrada fica atrás; ambas sem idade empatam")
    void desempateIdade_deveColocarOpcaoSemIdadeAtras() {
        // Arrange
        UUID comIdade = UUID.randomUUID();
        UUID semIdade = UUID.randomUUID();
        UUID outraSemIdade = UUID.randomUUID();
        DesempateIdade estrategia = new DesempateIdade(Map.of(comIdade, 40));

        // Act & Assert
        assertTrue(estrategia.compare(opcao(semIdade), opcao(comIdade)) > 0);
        assertTrue(estrategia.compare(opcao(comIdade), opcao(semIdade)) < 0);
        assertEquals(0, estrategia.compare(opcao(semIdade), opcao(outraSemIdade)));
    }

    @Test
    @DisplayName("Desempate alfabético: o nome que vem antes na ordem alfabética fica à frente")
    void desempateAlfabetico_devePriorizarOrdemAlfabetica() {
        // Arrange
        UUID idAna = UUID.randomUUID();
        UUID idZeca = UUID.randomUUID();
        DesempateAlfabetico estrategia =
                new DesempateAlfabetico(Map.of(idAna, "Ana", idZeca, "Zeca"));

        // Act & Assert
        assertTrue(estrategia.compare(opcao(idAna), opcao(idZeca)) < 0, "Ana deve vir antes");
        assertTrue(estrategia.compare(opcao(idZeca), opcao(idAna)) > 0, "Zeca deve vir depois");
    }

    @Test
    @DisplayName("Desempate alfabético: ignora maiúsculas/minúsculas e coloca opção sem nome atrás")
    void desempateAlfabetico_deveIgnorarCaixaETratarNomeAusente() {
        // Arrange
        UUID ana = UUID.randomUUID();
        UUID anaMaiuscula = UUID.randomUUID();
        UUID semNome = UUID.randomUUID();
        UUID outraSemNome = UUID.randomUUID();
        DesempateAlfabetico estrategia =
                new DesempateAlfabetico(Map.of(ana, "ana", anaMaiuscula, "ANA"));

        // Act & Assert
        assertEquals(0, estrategia.compare(opcao(ana), opcao(anaMaiuscula)));
        assertTrue(estrategia.compare(opcao(semNome), opcao(ana)) > 0);
        assertTrue(estrategia.compare(opcao(ana), opcao(semNome)) < 0);
        assertEquals(0, estrategia.compare(opcao(semNome), opcao(outraSemNome)));
    }

    @Test
    @DisplayName(
            "Desempate por sorteio: distingue as opções, de forma consistente e antissimétrica")
    void desempateAleatorio_deveSerConsistenteNaMesmaInstancia() {
        // Arrange: a estratégia sorteia os pesos no construtor
        UUID idCandidato1 = UUID.randomUUID();
        UUID idCandidato2 = UUID.randomUUID();
        DesempateAleatorio estrategia = new DesempateAleatorio(List.of(idCandidato1, idCandidato2));
        OpcaoApurada op1 = opcao(idCandidato1);
        OpcaoApurada op2 = opcao(idCandidato2);

        // Act
        int resultado1 = estrategia.compare(op1, op2);
        int resultado2 = estrategia.compare(op1, op2);
        int resultadoInvertido = estrategia.compare(op2, op1);

        // Assert: os pesos são inteiros sorteados, então coincidem com chance de 1 em 2^32
        assertNotEquals(0, resultado1, "O sorteio deve distinguir opções diferentes");
        assertEquals(resultado1, resultado2, "O sorteio deve ser consistente");
        assertEquals(
                Integer.signum(resultado1),
                -Integer.signum(resultadoInvertido),
                "A inversão dos parâmetros deve inverter o sinal");
    }
}
