package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Não permite iniciar uma eleição cuja regra de idade não possa ser executada. */
@Component
public class GuardaDesempatePorIdade implements GuardaPublicacao {
    private final VotacaoRepository votacoes;
    private final CandidatoOuChapaRepository candidatos;
    private final EstrategiaDesempateDaVotacao desempates;

    public GuardaDesempatePorIdade(
            VotacaoRepository votacoes,
            CandidatoOuChapaRepository candidatos,
            EstrategiaDesempateDaVotacao desempates) {
        this.votacoes = votacoes;
        this.candidatos = candidatos;
        this.desempates = desempates;
    }

    @Override
    public void validar(UUID id) {
        var v =
                votacoes.findById(id)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", id));
        // Regras e calendário ausentes são tratados pelas respectivas guardas.
        if (v.getTipo() == TipoVotacao.ELEICAO_PRIVADA
                && v.getRegras() != null
                && v.getFimEm() != null
                && v.getRegras().tipoDesempate() == TipoDesempate.IDADE) {
            desempates.criar(v, candidatos.findByVotacaoIdOrderByCriadoEm(id));
        }
    }
}
