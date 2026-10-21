package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.TransicaoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.TransicaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transições de estado pedidas pelo administrador e o histórico auditado delas (#24, RNF-03).
 *
 * <p>Um único endpoint recebe o estado de destino em vez de uma rota por ação: quem decide se a
 * transição vale é a tabela do {@code VotacaoStateService}, inclusive barrando as que só ocorrem
 * por data.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/transicoes")
public class TransicaoVotacaoController {

    private final VotacaoStateService stateService;

    public TransicaoVotacaoController(VotacaoStateService stateService) {
        this.stateService = stateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public void transitar(
            @PathVariable UUID votacaoId,
            @RequestBody @Valid TransicaoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        stateService.transitar(
                votacaoId, request.destino(), UUID.fromString(jwt.getSubject()), request.motivo());
    }

    @GetMapping
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public List<TransicaoResponse> historico(@PathVariable UUID votacaoId) {
        return stateService.historico(votacaoId).stream().map(TransicaoResponse::of).toList();
    }
}
