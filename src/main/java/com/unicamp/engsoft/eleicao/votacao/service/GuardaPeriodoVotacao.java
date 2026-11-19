package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Impede publicar o segundo turno antes de o administrador definir o calendário. */
@Component
public class GuardaPeriodoVotacao implements GuardaPublicacao {
    private final VotacaoRepository votacoes;

    public GuardaPeriodoVotacao(VotacaoRepository votacoes) {
        this.votacoes = votacoes;
    }

    @Override
    public void validar(UUID id) {
        var votacao =
                votacoes.findById(id)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", id));
        if (votacao.getInicioEm() == null
                || votacao.getFimEm() == null
                || !votacao.getFimEm().isAfter(votacao.getInicioEm())) {
            throw new DadosInvalidosException(
                    "Defina o início e o fim da votação antes de publicar");
        }
    }
}
