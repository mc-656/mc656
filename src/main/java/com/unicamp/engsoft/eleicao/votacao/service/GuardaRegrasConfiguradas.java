package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bloqueia a publicação enquanto as regras de apuração (RF-11) não forem configuradas: publicada
 * sem regras, a votação não teria como apurar o resultado.
 */
@Component
public class GuardaRegrasConfiguradas implements GuardaPublicacao {

    private final VotacaoRepository votacaoRepository;

    public GuardaRegrasConfiguradas(VotacaoRepository votacaoRepository) {
        this.votacaoRepository = votacaoRepository;
    }

    @Override
    public void validar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getRegras() == null) {
            throw new TransicaoInvalidaException(
                    votacaoId,
                    votacao.getEstado(),
                    EstadoVotacao.Publicada,
                    "as regras de apuração ainda não foram configuradas");
        }
    }
}
