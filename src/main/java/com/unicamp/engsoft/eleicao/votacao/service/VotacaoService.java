package com.unicamp.engsoft.eleicao.votacao.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;

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
        if(request.fimEm().isBefore(request.inicioEm())){
            throw new RegraDeNegocioException("A data de término deve ser posterior a data de início");
        }
        Votacao votacao = votacaoRepository.save(request.paraVotacao());
        papelVotacaoRepository.save(
                new PapelVotacao(administradorId, votacao.getId(), PapelUsuario.ADMIN_VOTACAO));

        //aí temos que adicionar os eleitores válidos para a votação
        return votacao;
    }
}