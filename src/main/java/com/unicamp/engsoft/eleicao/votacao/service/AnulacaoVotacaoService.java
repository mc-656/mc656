package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Anulação de votação em apuração (RF-42, #53): {@code Apuracao → Anulada}, estado final.
 *
 * <p>Os dois caminhos ficam em métodos separados, em vez de o chamador montar a transição, para que
 * a origem não possa ser trocada por engano: a falta de quórum é sempre do {@code SISTEMA} (sem
 * autor, com motivo fixo) e a irregularidade é sempre de um usuário com justificativa própria. A
 * transição em si, a auditoria (RNF-03) e a recusa a partir de outros estados continuam no {@link
 * VotacaoStateService}, único ponto de troca de estado (SPECS §7.3).
 */
@Service
public class AnulacaoVotacaoService {

    /** Motivo registrado na auditoria da anulação automática (decisão de 04/10 na #53). */
    public static final String MOTIVO_FALTA_DE_QUORUM = "quórum não atingido";

    private final VotacaoStateService stateService;

    public AnulacaoVotacaoService(VotacaoStateService stateService) {
        this.stateService = stateService;
    }

    /**
     * Anulação automática por falta de quórum, chamada pela apuração (#50) quando o quórum mínimo
     * não é atingido. Origem {@code SISTEMA}, autor nulo. Conferir o quórum é responsabilidade do
     * chamador: só a apuração tem os votos e a regra para isso.
     */
    public void anularPorFaltaDeQuorum(UUID votacaoId) {
        stateService.transitar(votacaoId, EstadoVotacao.Anulada, null, MOTIVO_FALTA_DE_QUORUM);
    }

    /**
     * Anulação manual por irregularidade, decidida pelo {@code ADMIN_VOTACAO} (a autorização fica
     * no controller). A justificativa obrigatória é conferida pelo state service ({@code
     * EXIGEM_MOTIVO}), o mesmo que vale para quem anula pelo endpoint genérico de transições. O
     * autor é conferido aqui porque um autor nulo viraria silenciosamente uma anulação de origem
     * {@code SISTEMA}, o que é erro de programação e não dado inválido do cliente.
     *
     * @throws DadosInvalidosException se a justificativa for nula ou em branco.
     */
    public void anularPorIrregularidade(UUID votacaoId, UUID autorId, String justificativa) {
        Objects.requireNonNull(autorId, "anulação por irregularidade exige autor");
        stateService.transitar(votacaoId, EstadoVotacao.Anulada, autorId, justificativa);
    }
}
