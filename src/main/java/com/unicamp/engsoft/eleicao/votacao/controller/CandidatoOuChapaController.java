package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CandidatoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Candidatos/chapas de uma votação (RF-12). Escrita só para {@code ADMIN_VOTACAO}; a listagem
 * também é aberta ao {@code ELEITOR}, que precisa saber em quem pode votar.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/candidatos")
public class CandidatoOuChapaController {

    private final CandidatoOuChapaService candidatoService;

    public CandidatoOuChapaController(CandidatoOuChapaService candidatoService) {
        this.candidatoService = candidatoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public CandidatoResponse cadastrar(
            @PathVariable UUID votacaoId, @RequestBody @Valid CadastrarCandidatoRequest request) {
        return CandidatoResponse.of(candidatoService.cadastrar(votacaoId, request));
    }

    @GetMapping
    @PreAuthorize(
            "@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)"
                    + " or @autorizacaoVotacao.ehEleitor(#votacaoId, authentication)")
    public List<CandidatoResponse> listar(@PathVariable UUID votacaoId) {
        return candidatoService.listar(votacaoId).stream().map(CandidatoResponse::of).toList();
    }

    @DeleteMapping("/{candidatoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public void remover(@PathVariable UUID votacaoId, @PathVariable UUID candidatoId) {
        candidatoService.remover(votacaoId, candidatoId);
    }
}
