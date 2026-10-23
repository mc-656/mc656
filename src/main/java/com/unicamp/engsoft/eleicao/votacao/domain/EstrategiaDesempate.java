package com.unicamp.engsoft.eleicao.votacao.domain;
import java.util.Comparator;


@FunctionalInterface
public interface EstrategiaDesempate extends Comparator<OpcaoApurada>{
    @Override
    int compare(OpcaoApurada a1, OpcaoApurada a2);// se for 1 opcao 1 ganha se for -1 opção 2 está ganhando
}