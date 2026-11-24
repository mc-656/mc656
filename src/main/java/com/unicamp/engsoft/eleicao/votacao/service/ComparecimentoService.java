package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.CalculoComparecimento;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ComparecimentoResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acompanhamento agregado de comparecimento durante a janela de votação (RF-40, #51/#86).
 *
 * <p>Consulta apenas {@code participacoes} e {@code papeis_votacao}: nunca lê {@code cedulas} nem
 * {@code alocacoes_orcamento}, garantindo o sigilo por opção (RNF-02) mesmo para o administrador.
 */
@Service
public class ComparecimentoService {

    private final VotacaoRepository votacaoRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final PapelVotacaoRepository papelVotacaoRepository;

    public ComparecimentoService(
            VotacaoRepository votacaoRepository,
            ParticipacaoRepository participacaoRepository,
            PapelVotacaoRepository papelVotacaoRepository) {
        this.votacaoRepository = votacaoRepository;
        this.participacaoRepository = participacaoRepository;
        this.papelVotacaoRepository = papelVotacaoRepository;
    }

    @Transactional(readOnly = true)
    public ComparecimentoResponse consultar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        EstadoVotacao estado = votacao.getEstado();
        if (estado != EstadoVotacao.EmVotacao && estado != EstadoVotacao.Suspensa) {
            throw new RegraDeNegocioException(
                    "O comparecimento em tempo real só pode ser consultado com a votação em"
                            + " EmVotacao ou Suspensa (estado atual: %s)".formatted(estado));
        }

        long totalParticipacoes = participacaoRepository.countByVotacaoId(votacaoId);
        long totalEleitoresAptos =
                papelVotacaoRepository.countByIdVotacaoIdAndIdPapel(
                        votacaoId, PapelUsuario.ELEITOR);
        BigDecimal quorumMinimo =
                votacao.getRegras() != null ? votacao.getRegras().quorumMinimoPercentual() : null;
        BigDecimal percentual =
                CalculoComparecimento.percentual(totalEleitoresAptos, totalParticipacoes);
        boolean quorumAtingido =
                CalculoComparecimento.quorumAtingido(
                        quorumMinimo, totalEleitoresAptos, totalParticipacoes);

        return new ComparecimentoResponse(
                votacaoId,
                estado,
                totalParticipacoes,
                totalEleitoresAptos,
                percentual,
                quorumMinimo,
                quorumAtingido);
    }
}
