package com.unicamp.engsoft.eleicao.votacao.service;
import com.unicamp.engsoft.eleicao.votacao.domain.OpcaoApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempate;
import java.util.UUID;
import java.util.Map;

public class DesempateIdade implements EstrategiaDesempate{
    private final Map<UUID, Integer> opcoes;

    public DesempateIdade(Map<UUID, Integer> opcoes){
        this.idades = idades; 
    }
    
    @Override
    public int compare(OpcaoApurada a1, OpcaoApurada a2){
        Integer idade1 = idades.get(a1.opcaoId());
        Integer idade2 = idades.get(a2.opcaoId());
        if (idade1 == null && idade2 == null) return 0; // nenhum ganhou
        if (idade1 == null) return 1; // opcao 1 vai ganhar
        if (idade2 == null) return -1; // opção 2 ganhar

        return idade2.compareTo(idade1);
    }
}