package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validação de elegibilidade de um usuário para uma votação (RF-02), consultada antes de qualquer
 * operação de voto.
 *
 * <p>Elegível é quem tem o papel {@code ELEITOR} na votação ({@code papeis_votacao}); não existe
 * lista de elegíveis separada. A checagem do papel é delegada a {@link
 * AutorizacaoVotacao#ehEleitor} para que {@code @PreAuthorize} e este serviço nunca divirjam.
 *
 * <p>Diferente do {@code @PreAuthorize}, que só responde sim/não, aqui a recusa é explícita:
 * votação inexistente vira 404 e usuário não elegível vira {@link UsuarioNaoElegivelException}. A
 * existência é checada primeiro porque, sem ela, toda votação inexistente pareceria apenas "não
 * elegível" e esconderia erros de id no chamador.
 */
@Service
public class ElegibilidadeService {

    private final VotacaoRepository votacaoRepository;
    private final AutorizacaoVotacao autorizacaoVotacao;

    public ElegibilidadeService(
            VotacaoRepository votacaoRepository, AutorizacaoVotacao autorizacaoVotacao) {
        this.votacaoRepository = votacaoRepository;
        this.autorizacaoVotacao = autorizacaoVotacao;
    }

    /**
     * Garante que o usuário autenticado é eleitor da votação.
     *
     * @throws RecursoNaoEncontradoException se a votação não existe
     * @throws UsuarioNaoElegivelException se o usuário não tem papel {@code ELEITOR} nela
     */
    @Transactional(readOnly = true)
    public void validar(UUID votacaoId, Authentication autenticacao) {
        if (votacaoId == null || !votacaoRepository.existsById(votacaoId)) {
            throw new RecursoNaoEncontradoException("Votação", votacaoId);
        }
        if (!autorizacaoVotacao.ehEleitor(votacaoId, autenticacao)) {
            throw new UsuarioNaoElegivelException(votacaoId);
        }
    }
}
