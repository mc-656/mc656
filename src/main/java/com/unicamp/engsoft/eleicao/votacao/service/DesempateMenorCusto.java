package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempateOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoDesempate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Critério de desempate para orçamento participativo (#95): prioriza propostas de menor custo,
 * permitindo aprovar mais iniciativas dentro do orçamento disponível; em igualdade de custo, aplica
 * critério secundário (ordem alfabética quando configurado) ou mantém as propostas empatadas.
 */
public class DesempateMenorCusto implements EstrategiaDesempateOrcamento {

    private final TipoDesempate desempateSecundario;

    public DesempateMenorCusto(TipoDesempate desempateSecundario) {
        this.desempateSecundario =
                desempateSecundario == null ? TipoDesempate.NENHUM : desempateSecundario;
    }

    @Override
    public List<List<PropostaApuravel>> ordenarEmpatadas(List<PropostaApuravel> empatadas) {
        Objects.requireNonNull(empatadas, "lista de empatadas é obrigatória");
        Map<BigDecimal, List<PropostaApuravel>> porCusto = new TreeMap<>();
        for (PropostaApuravel proposta : empatadas) {
            porCusto.computeIfAbsent(proposta.custo(), k -> new ArrayList<>()).add(proposta);
        }
        List<List<PropostaApuravel>> subgrupos = new ArrayList<>();
        for (List<PropostaApuravel> mesmoCusto : porCusto.values()) {
            if (desempateSecundario == TipoDesempate.ORDEM_ALFABETICA) {
                mesmoCusto.stream()
                        .sorted(
                                Comparator.comparing(
                                                PropostaApuravel::titulo,
                                                String.CASE_INSENSITIVE_ORDER)
                                        .thenComparing(PropostaApuravel::id))
                        .map(List::of)
                        .forEach(subgrupos::add);
            } else {
                subgrupos.add(List.copyOf(mesmoCusto));
            }
        }
        return List.copyOf(subgrupos);
    }
}
