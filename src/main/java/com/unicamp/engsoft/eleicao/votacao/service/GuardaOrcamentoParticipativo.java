package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bloqueia a publicação de orçamento participativo enquanto propostas e orçamento total (RF-20) não
 * existem: publicada, a votação não teria em que votar. Remover no M3.
 */
@Component
public class GuardaOrcamentoParticipativo implements GuardaPublicacao {

    private final VotacaoRepository votacaoRepository;

    public GuardaOrcamentoParticipativo(VotacaoRepository votacaoRepository) {
        this.votacaoRepository = votacaoRepository;
    }

    @Override
    public void validar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getTipo() == TipoVotacao.ORCAMENTO_PARTICIPATIVO) {
            throw new TransicaoInvalidaException(
                    votacaoId,
                    votacao.getEstado(),
                    EstadoVotacao.Publicada,
                    "orçamento participativo ainda não suportado");
        }
    }
}
