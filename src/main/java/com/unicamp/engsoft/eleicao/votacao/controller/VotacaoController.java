package com.unicamp.engsoft.eleicao.votacao.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/votacoes")
public class VotacaoController {

    private final VotacaoService votacaoService;

    public VotacaoController(VotacaoService votacaoService) {
        this.votacaoService = votacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VotacaoResponse criar(
            @RequestBody @Valid CriarVotacaoRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID administradorId = UUID.fromString(jwt.getSubject()); // pegar quem é a pessoa que está fazendo a requisição
        return VotacaoResponse.of(votacaoService.criarVotacao(request, administradorId));
    }
}