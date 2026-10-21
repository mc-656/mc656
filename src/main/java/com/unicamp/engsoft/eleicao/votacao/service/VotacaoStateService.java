package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import java.util.List;
import java.util.UUID;

/**
 * Contrato de serviço responsável por gerenciar as transições de estado da máquina de estados de
 * uma {@code Votacao}.
 *
 * <p>Garante que todas as transições sigam o fluxo estipulado no modelo de domínio e registra a
 * auditoria de quem realizou a alteração e por qual motivo (RNF-03).
 */
public interface VotacaoStateService {

    /**
     * Realiza a transição de estado de uma votação de forma auditada.
     *
     * @param votacaoId Identificador único da votação.
     * @param destino Novo estado desejado para a votação.
     * @param autorId Identificador do usuário que solicitou a transição, ou {@code null} quando a
     *     transição é automática (job agendado por data). Nulo implica origem {@code SISTEMA} na
     *     auditoria; transições que só ocorrem por data rejeitam autor, e as demais o exigem.
     * @param motivo Justificativa ou motivo da transição de estado. Opcional.
     * @throws TransicaoInvalidaException Caso a transição seja negada pelas regras da máquina de
     *     estados ou pelas guardas de publicação.
     */
    void transitar(UUID votacaoId, EstadoVotacao destino, UUID autorId, String motivo)
            throws TransicaoInvalidaException;

    /** Transições já realizadas pela votação, da mais antiga para a mais recente (RNF-03). */
    List<TransicaoVotacao> historico(UUID votacaoId);
}
