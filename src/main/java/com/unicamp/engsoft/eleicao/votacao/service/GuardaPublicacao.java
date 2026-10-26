package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import java.util.UUID;

/**
 * Interface plugável para validações executadas antes de transitar uma votação do estado {@code
 * RascunhoVotacao} para {@code Publicada}.
 */
public interface GuardaPublicacao {

    /**
     * Executa a verificação/validação das pré-condições sob responsabilidade deste guarda.
     *
     * @param votacaoId Identificador único da votação a ser publicada.
     * @throws TransicaoInvalidaException Se a validação falhar e a publicação deva ser impedida.
     */
    void validar(UUID votacaoId) throws TransicaoInvalidaException;
}
