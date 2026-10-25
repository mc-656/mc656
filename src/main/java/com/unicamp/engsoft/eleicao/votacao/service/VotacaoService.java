package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VotacaoService {

    private final VotacaoRepository votacaoRepository;
    private final PapelVotacaoRepository papelVotacaoRepository;

    public VotacaoService(
            VotacaoRepository votacaoRepository, PapelVotacaoRepository papelVotacaoRepository) {
        this.votacaoRepository = votacaoRepository;
        this.papelVotacaoRepository = papelVotacaoRepository;
    }

    @Transactional
    public Votacao criarVotacao(CriarVotacaoRequest request, UUID administradorId) {
        if (request.fimEm().isBefore(request.inicioEm())) {
            throw new RegraDeNegocioException(
                    "A data de término deve ser posterior a data de início");
        }
        Votacao votacao = votacaoRepository.save(request.paraVotacao());
        papelVotacaoRepository.save(
                new PapelVotacao(administradorId, votacao.getId(), PapelUsuario.ADMIN_VOTACAO));

        // aí temos que adicionar os eleitores válidos para a votação
        return votacao;
    }

    /**
     * Configura as regras de apuração da votação (RF-11). Só enquanto ela está em {@code
     * RascunhoVotacao}: depois de publicada, eleitores já podem tê-la consultado e mudar a regra de
     * apuração mudaria o resultado esperado. A autorização (só {@code ADMIN_VOTACAO}) fica no
     * controller, via {@code @PreAuthorize}.
     *
     * <p>A combinação de parâmetros é validada pelo value object antes de qualquer consulta, para
     * que um payload inválido devolva 400 mesmo quando a votação nem existe.
     */
    @Transactional
    public RegraVotacao configurarRegras(UUID votacaoId, ConfigurarRegrasRequest request) {
        RegraVotacao regra = request.paraRegra();
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getEstado() != EstadoVotacao.RascunhoVotacao) {
            throw new RegraDeNegocioException(
                    "Regras só podem ser alteradas com a votação em rascunho (estado atual: %s)"
                            .formatted(votacao.getEstado()));
        }
        votacao.configurarRegras(regra);
        return regra;
    }
}
