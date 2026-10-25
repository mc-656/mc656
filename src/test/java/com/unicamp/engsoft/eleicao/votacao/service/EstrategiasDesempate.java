package com.unicamp.engsoft.eleicao.votacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unicamp.engsoft.eleicao.votacao.domain.OpcaoApurada;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstrategiasDesempateTest {

    @Test
    void desempateIdade_devePriorizarCandidatoMaisVelho() {
        UUID idMaisVelho = UUID.randomUUID();
        UUID idMaisNovo = UUID.randomUUID();

        Map<UUID, Integer> idades =
                Map.of(
                        idMaisVelho, 55,
                        idMaisNovo, 30);

        DesempateIdade estrategia = new DesempateIdade(idades);

        OpcaoApurada opMaisVelho = new OpcaoApurada(idMaisVelho, BigDecimal.TEN, BigDecimal.TEN);
        OpcaoApurada opMaisNovo = new OpcaoApurada(idMaisNovo, BigDecimal.TEN, BigDecimal.TEN);

        // negativo significa que o primeiro parâmetro ganha
        assertTrue(estrategia.compare(opMaisVelho, opMaisNovo) < 0, "O mais velho deve vencer");

        // positivo significa que o primeiro parâmetro perde
        assertTrue(estrategia.compare(opMaisNovo, opMaisVelho) > 0, "O mais novo deve perder");

        // zero mantém empate se idades forem iguais
        assertEquals(0, estrategia.compare(opMaisVelho, opMaisVelho), "Mesma idade deve empatar");
    }

    @Test
    void desempateAlfabetico_devePriorizarOrdemAlfabetica() {
        UUID idAna = UUID.randomUUID();
        UUID idZeca = UUID.randomUUID();

        Map<UUID, String> nomes =
                Map.of(
                        idAna, "Ana",
                        idZeca, "Zeca");

        DesempateAlfabetico estrategia = new DesempateAlfabetico(nomes);

        OpcaoApurada opAna = new OpcaoApurada(idAna, BigDecimal.TEN, BigDecimal.TEN);
        OpcaoApurada opZeca = new OpcaoApurada(idZeca, BigDecimal.TEN, BigDecimal.TEN);

        // 'A' > 'Z', Ana vence)
        assertTrue(estrategia.compare(opAna, opZeca) < 0, "Ana deve vir antes de Zeca");
        assertTrue(estrategia.compare(opZeca, opAna) > 0, "Zeca deve vir depois de Ana");
    }

    @Test
    void desempateAleatorio_deveSerConsistenteNaMesmaInstancia() {
        UUID idCandidato1 = UUID.randomUUID();
        UUID idCandidato2 = UUID.randomUUID();

        // A estratégia sorteia os pesos no construtor
        DesempateAleatorio estrategia = new DesempateAleatorio(List.of(idCandidato1, idCandidato2));

        OpcaoApurada op1 = new OpcaoApurada(idCandidato1, BigDecimal.TEN, BigDecimal.TEN);
        OpcaoApurada op2 = new OpcaoApurada(idCandidato2, BigDecimal.TEN, BigDecimal.TEN);

        int resultado1 = estrategia.compare(op1, op2);
        int resultado2 = estrategia.compare(op1, op2);
        int resultadoInvertido = estrategia.compare(op2, op1);

        // O resultado não pode mudar se chamarmos o compare várias vezes na mesma apuração
        assertEquals(
                resultado1, resultado2, "O sorteio deve ser consistente em múltiplas chamadas");

        // O inverso deve ter sinais opostos para o sort do Java funcionar
        assertTrue(
                resultado1 == -resultadoInvertido || resultado1 == 0,
                "A inversão dos parâmetros deve inverter o sinal");
    }
}
