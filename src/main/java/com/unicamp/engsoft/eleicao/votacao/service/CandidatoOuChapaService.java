package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
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
 * podem tê-la consultado, e mudar as opções alteraria a eleição que eles viram. Essa regra não mora
 * aqui: este service só declara a operação ({@link OperacaoVotacao#GERENCIAR_CANDIDATOS}) e delega
 * a checagem — com lock — a {@link ExigenciaDeEstadoDaVotacao}, compartilhada com os demais
 * services. A autorização (só {@code ADMIN_VOTACAO} escreve) fica no controller, via
 * {@code @PreAuthorize}.
 */
@Service
public class CandidatoOuChapaService {

    private final CandidatoOuChapaRepository candidatoRepository;
    private final VotacaoRepository votacaoRepository;
    private final ExigenciaDeEstadoDaVotacao exigenciaDeEstado;

    public CandidatoOuChapaService(
            CandidatoOuChapaRepository candidatoRepository,
            VotacaoRepository votacaoRepository,
            ExigenciaDeEstadoDaVotacao exigenciaDeEstado) {
        this.candidatoRepository = candidatoRepository;
        this.votacaoRepository = votacaoRepository;
        this.exigenciaDeEstado = exigenciaDeEstado;
    }

    @Transactional
    public CandidatoOuChapa cadastrar(UUID votacaoId, CadastrarCandidatoRequest request) {
        exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.GERENCIAR_CANDIDATOS);
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
        exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.GERENCIAR_CANDIDATOS);
        CandidatoOuChapa candidato =
                candidatoRepository
                        .findByIdAndVotacaoId(candidatoId, votacaoId)
                        .orElseThrow(
                                () ->
                                        new RecursoNaoEncontradoException(
                                                "Candidato/chapa", candidatoId));
        candidatoRepository.delete(candidato);
    }
}
