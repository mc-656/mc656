package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Eleição privada só é publicada com ao menos {@value #MINIMO_CANDIDATOS} candidatos/chapas
 * (RF-12): com menos, não há escolha a fazer. Outros tipos de votação não são afetados.
 */
@Component
public class GuardaMinimoCandidatos implements GuardaPublicacao {

    static final int MINIMO_CANDIDATOS = 2;

    private final VotacaoRepository votacaoRepository;
    private final CandidatoOuChapaRepository candidatoRepository;

    public GuardaMinimoCandidatos(
            VotacaoRepository votacaoRepository, CandidatoOuChapaRepository candidatoRepository) {
        this.votacaoRepository = votacaoRepository;
        this.candidatoRepository = candidatoRepository;
    }

    @Override
    public void validar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getTipo() != TipoVotacao.ELEICAO_PRIVADA) {
            return;
        }
        if (candidatoRepository.countByVotacaoId(votacaoId) < MINIMO_CANDIDATOS) {
            throw new TransicaoInvalidaException(
                    votacaoId,
                    votacao.getEstado(),
                    EstadoVotacao.Publicada,
                    "eleição privada precisa de no mínimo %d candidatos/chapas"
                            .formatted(MINIMO_CANDIDATOS));
        }
    }
}
