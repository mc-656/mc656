package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.OpcaoApurada;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DesempateAleatorio implements EstrategiaDesempate {

    private final Map<UUID, Integer> pesosSorteados;

    public DesempateAleatorio(List<UUID> candidatosIds) {
        this.pesosSorteados = new HashMap<>();
        Random random = new Random();

        for (UUID id : candidatosIds) {
            pesosSorteados.put(id, random.nextInt());
        }
    }

    @Override
    public int compare(OpcaoApurada a1, OpcaoApurada a2) {
        // Se um candidato não estiver no mapa, ganha peso 0 por segurança
        Integer sorteio1 = pesosSorteados.getOrDefault(a1.opcaoId(), 0);
        Integer sorteio2 = pesosSorteados.getOrDefault(a2.opcaoId(), 0);

        //  maior número sorteado sobe no ranking
        return sorteio2.compareTo(sorteio1);
    }
}
