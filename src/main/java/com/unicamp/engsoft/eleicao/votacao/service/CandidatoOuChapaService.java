package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de candidatos/chapas de uma votação (RF-12).
 *
 * <p>A lista só muda com a votação em {@code RascunhoVotacao}: depois de publicada, eleitores já
 * podem tê-la consultado, e mudar as opções alteraria a eleição que eles viram. A autorização (só
 * {@code ADMIN_VOTACAO} escreve) fica no controller, via {@code @PreAuthorize}.
 */
@Service
public class CandidatoOuChapaService {

    private final CandidatoOuChapaRepository candidatoRepository;
    private final VotacaoRepository votacaoRepository;

    public CandidatoOuChapaService(
            CandidatoOuChapaRepository candidatoRepository, VotacaoRepository votacaoRepository) {
        this.candidatoRepository = candidatoRepository;
        this.votacaoRepository = votacaoRepository;
    }

    @Transactional
    public CandidatoOuChapa cadastrar(UUID votacaoId, CadastrarCandidatoRequest request) {
        exigirRascunho(votacaoId);
        String nome = request.nome().strip();
        if (candidatoRepository.existsByVotacaoIdAndNomeIgnoreCase(votacaoId, nome)) {
            throw new RegraDeNegocioException(
                    "Já existe candidato/chapa com o nome '%s' nesta votação".formatted(nome));
        }
        return candidatoRepository.save(new CandidatoOuChapa(votacaoId, nome, request.descricao()));
    }

    @Transactional(readOnly = true)
    public List<CandidatoOuChapa> listar(UUID votacaoId) {
        if (!votacaoRepository.existsById(votacaoId)) {
            throw new RecursoNaoEncontradoException("Votação", votacaoId);
        }
        return candidatoRepository.findByVotacaoIdOrderByCriadoEm(votacaoId);
    }

    @Transactional
    public void remover(UUID votacaoId, UUID candidatoId) {
        exigirRascunho(votacaoId);
        CandidatoOuChapa candidato =
                candidatoRepository
                        .findByIdAndVotacaoId(candidatoId, votacaoId)
                        .orElseThrow(
                                () ->
                                        new RecursoNaoEncontradoException(
                                                "Candidato/chapa", candidatoId));
        candidatoRepository.delete(candidato);
    }

    private void exigirRascunho(UUID votacaoId) {
        EstadoVotacao estado =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId))
                        .getEstado();
        if (estado != EstadoVotacao.RascunhoVotacao) {
            throw new RegraDeNegocioException(
                    "Candidatos/chapas só podem ser alterados com a votação em rascunho (estado"
                            + " atual: %s)".formatted(estado));
        }
    }
}
