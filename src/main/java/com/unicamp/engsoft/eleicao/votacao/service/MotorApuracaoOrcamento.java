package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.AlocacaoApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempateOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracaoOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusPropostaApurada;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Motor puro de apuração por cotas do Orçamento Participativo (RF-23, #130, #95).
 *
 * <p>Ordena propostas da maior para a menor pontuação e seleciona gulosamente as que cabem no
 * orçamento restante; se uma proposta ultrapassa o saldo disponível, ela não é selecionada e o
 * motor segue avaliando as próximas do ranking que ainda caibam no orçamento. Empates na fronteira
 * do orçamento são tratados pela {@link EstrategiaDesempateOrcamento}.
 */
@Service
public class MotorApuracaoOrcamento {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public ResultadoApuracaoOrcamento apurar(
            BigDecimal orcamentoTotal,
            BigDecimal quorumMinimoPercentual,
            List<PropostaApuravel> propostas,
            List<AlocacaoApuravel> alocacoes,
            long totalEleitoresAptos,
            long totalParticipantes,
            EstrategiaDesempateOrcamento desempate) {
        Objects.requireNonNull(orcamentoTotal, "orçamento total é obrigatório");
        if (orcamentoTotal.signum() <= 0) {
            throw new IllegalArgumentException("O orçamento total deve ser maior que zero");
        }
        Objects.requireNonNull(propostas, "lista de propostas é obrigatória");
        if (propostas.isEmpty()) {
            throw new IllegalArgumentException("Ao menos uma proposta é necessária para apurar");
        }
        Objects.requireNonNull(alocacoes, "lista de alocações é obrigatória");
        Objects.requireNonNull(desempate, "estratégia de desempate é obrigatória");
        if (totalEleitoresAptos < 0 || totalParticipantes < 0) {
            throw new IllegalArgumentException("Contagens de participação não podem ser negativas");
        }
        if (totalParticipantes > totalEleitoresAptos) {
            throw new IllegalArgumentException(
                    "Participantes não pode exceder o total de eleitores aptos");
        }

        Map<UUID, BigDecimal> pontosPorProposta = new HashMap<>();
        for (PropostaApuravel p : propostas) {
            pontosPorProposta.put(p.id(), BigDecimal.ZERO);
        }
        BigDecimal totalPontosComputados = BigDecimal.ZERO;
        for (AlocacaoApuravel alocacao : alocacoes) {
            BigDecimal acumulado = pontosPorProposta.get(alocacao.propostaId());
            if (acumulado == null) {
                throw new IllegalArgumentException(
                        "Alocação referencia proposta desconhecida: " + alocacao.propostaId());
            }
            pontosPorProposta.put(alocacao.propostaId(), acumulado.add(alocacao.pontos()));
            totalPontosComputados = totalPontosComputados.add(alocacao.pontos());
        }

        BigDecimal percentualComparecimento =
                totalEleitoresAptos == 0
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.valueOf(totalParticipantes)
                                .multiply(CEM)
                                .divide(
                                        BigDecimal.valueOf(totalEleitoresAptos),
                                        2,
                                        RoundingMode.HALF_UP);
        boolean quorumAtingido =
                quorumMinimoPercentual == null
                        || (totalEleitoresAptos > 0
                                && percentualComparecimento.compareTo(quorumMinimoPercentual) >= 0);

        if (!quorumAtingido) {
            List<PropostaApurada> semQuorum =
                    propostas.stream()
                            .sorted(
                                    Comparator.comparing(
                                                    (PropostaApuravel p) ->
                                                            pontosPorProposta.get(p.id()),
                                                    Comparator.reverseOrder())
                                            .thenComparing(PropostaApuravel::criadoEm))
                            .map(
                                    p ->
                                            new PropostaApurada(
                                                    p.id(),
                                                    pontosPorProposta.get(p.id()),
                                                    p.custo(),
                                                    StatusPropostaApurada.QUORUM_NAO_ATINGIDO))
                            .toList();
            return new ResultadoApuracaoOrcamento(
                    StatusApuracao.QUORUM_NAO_ATINGIDO,
                    orcamentoTotal,
                    BigDecimal.ZERO,
                    orcamentoTotal,
                    semQuorum,
                    totalPontosComputados,
                    totalParticipantes,
                    totalEleitoresAptos,
                    percentualComparecimento,
                    false);
        }

        NavigableMap<BigDecimal, List<PropostaApuravel>> porPontos =
                new TreeMap<>(Comparator.reverseOrder());
        for (PropostaApuravel p : propostas) {
            porPontos.computeIfAbsent(pontosPorProposta.get(p.id()), k -> new ArrayList<>()).add(p);
        }

        BigDecimal saldoRestante = orcamentoTotal;
        boolean houveEmpateSemDecisao = false;
        List<PropostaApurada> apuradas = new ArrayList<>(propostas.size());

        for (List<PropostaApuravel> grupo : porPontos.values()) {
            List<List<PropostaApuravel>> subgrupos = desempate.ordenarEmpatadas(grupo);
            for (List<PropostaApuravel> subgrupo : subgrupos) {
                BigDecimal pontos = pontosPorProposta.get(subgrupo.getFirst().id());
                if (subgrupo.size() == 1) {
                    PropostaApuravel p = subgrupo.getFirst();
                    if (p.custo().compareTo(saldoRestante) <= 0) {
                        saldoRestante = saldoRestante.subtract(p.custo());
                        apuradas.add(
                                new PropostaApurada(
                                        p.id(),
                                        pontos,
                                        p.custo(),
                                        StatusPropostaApurada.SELECIONADA));
                    } else {
                        apuradas.add(
                                new PropostaApurada(
                                        p.id(),
                                        pontos,
                                        p.custo(),
                                        StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO));
                    }
                    continue;
                }

                BigDecimal somaCustos =
                        subgrupo.stream()
                                .map(PropostaApuravel::custo)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal limiteAtual = saldoRestante;
                if (somaCustos.compareTo(saldoRestante) <= 0) {
                    saldoRestante = saldoRestante.subtract(somaCustos);
                    for (PropostaApuravel p : subgrupo) {
                        apuradas.add(
                                new PropostaApurada(
                                        p.id(),
                                        pontos,
                                        p.custo(),
                                        StatusPropostaApurada.SELECIONADA));
                    }
                } else if (subgrupo.stream().allMatch(p -> p.custo().compareTo(limiteAtual) > 0)) {
                    for (PropostaApuravel p : subgrupo) {
                        apuradas.add(
                                new PropostaApurada(
                                        p.id(),
                                        pontos,
                                        p.custo(),
                                        StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO));
                    }
                } else {
                    houveEmpateSemDecisao = true;
                    for (PropostaApuravel p : subgrupo) {
                        apuradas.add(
                                new PropostaApurada(
                                        p.id(),
                                        pontos,
                                        p.custo(),
                                        StatusPropostaApurada.EMPATADA_SEM_DECISAO));
                    }
                }
            }
        }

        BigDecimal orcamentoUtilizado = orcamentoTotal.subtract(saldoRestante);
        StatusApuracao status =
                houveEmpateSemDecisao ? StatusApuracao.EMPATE : StatusApuracao.DEFINIDO;
        return new ResultadoApuracaoOrcamento(
                status,
                orcamentoTotal,
                orcamentoUtilizado,
                saldoRestante,
                apuradas,
                totalPontosComputados,
                totalParticipantes,
                totalEleitoresAptos,
                percentualComparecimento,
                true);
    }
}
