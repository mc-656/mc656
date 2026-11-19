package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstrategiaDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Carrega apenas dados das opções, nunca dados de eleitores, para desempatar. */
@Component
public class EstrategiaDesempateDaVotacao {
    public EstrategiaDesempate criar(Votacao votacao, List<CandidatoOuChapa> opcoes) {
        return switch (votacao.getRegras().tipoDesempate()) {
            case NENHUM -> null;
            case ORDEM_ALFABETICA ->
                    new DesempateAlfabetico(
                            opcoes.stream()
                                    .collect(
                                            Collectors.toMap(
                                                    CandidatoOuChapa::getId,
                                                    CandidatoOuChapa::getNome)));
            case SORTEIO ->
                    new DesempateAleatorio(opcoes.stream().map(CandidatoOuChapa::getId).toList());
            case IDADE -> porIdade(votacao, opcoes);
        };
    }

    private EstrategiaDesempate porIdade(Votacao votacao, List<CandidatoOuChapa> opcoes) {
        LocalDate referencia = LocalDate.ofInstant(votacao.getFimEm(), ZoneOffset.UTC);
        Map<UUID, Integer> idades = new HashMap<>();
        for (CandidatoOuChapa opcao : opcoes) {
            if (opcao.getDataNascimento() == null
                    || opcao.getDataNascimento().isAfter(referencia)) {
                throw new DadosInvalidosException(
                        "Desempate por idade exige data de nascimento válida de todas as opções");
            }
            // Dias preservam a ordem inclusive para candidatos com a mesma idade em anos.
            idades.put(
                    opcao.getId(),
                    Math.toIntExact(
                            ChronoUnit.DAYS.between(opcao.getDataNascimento(), referencia)));
        }
        return new DesempateIdade(idades);
    }
}
