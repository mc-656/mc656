package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Orçamento participativo só é publicado com orçamento total definido e ao menos uma proposta
 * (RF-20): sem orçamento, a apuração por cotas (RF-23) não tem limite; sem propostas, não há em que
 * distribuir pontos. Outros tipos de votação não são afetados.
 *
 * <p>Não revalida "custo ≤ orçamento": o {@code PropostaService} mantém essa invariante a cada
 * escrita, sob o lock da votação.
 */
@Component
public class GuardaOrcamentoParticipativo implements GuardaPublicacao {

    private final VotacaoRepository votacaoRepository;
    private final PropostaRepository propostaRepository;

    public GuardaOrcamentoParticipativo(
            VotacaoRepository votacaoRepository, PropostaRepository propostaRepository) {
        this.votacaoRepository = votacaoRepository;
        this.propostaRepository = propostaRepository;
    }

    @Override
    public void validar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getTipo() != TipoVotacao.ORCAMENTO_PARTICIPATIVO) {
            return;
        }
        if (votacao.getOrcamentoTotal() == null) {
            throw bloqueio(votacao, "o orçamento total ainda não foi definido");
        }
        if (propostaRepository.countByVotacaoId(votacaoId) == 0) {
            throw bloqueio(votacao, "orçamento participativo precisa de ao menos uma proposta");
        }
    }

    private static TransicaoInvalidaException bloqueio(Votacao votacao, String motivo) {
        return new TransicaoInvalidaException(
                votacao.getId(), votacao.getEstado(), EstadoVotacao.Publicada, motivo);
    }
}
