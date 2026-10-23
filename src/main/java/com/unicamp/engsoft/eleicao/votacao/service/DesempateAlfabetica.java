package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.domain.EstrategiaDesempate;
import com.unicamp.engsoft.eleicao.domain.OpcaoApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempate;
import java.util.UUID;
import java.util.Map;


public class DesempateAlfabetica implements EstrategiaDesempate{
    private final Map<UUID, String> opcoes;

    public DesempateAlfabetica(Map<UUID, String> opcoes){
        this.opcoes = opcoes; 
    }
    
    @Override
    int compare(OpcaoApurada a1, OpcaoApurada a2){
        String nome1 = opcoes.get(a1.opcaoId());
        String nome2= opcoes.get(a2.opcaoId());
        if(nome1 == null && nome2 == null) return 0; // nenhum ganhou
        if(nome1 == null) return 1; //opcao 1 vai ganhar 
        if(nome2 == null) return -1; //opção 2 ganhar

        return nome1.ComparetoIgnoreCase(nome2);
    }
}


