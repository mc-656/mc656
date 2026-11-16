package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.domain.ModeloVotacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoPorModeloRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ModeloVotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoPorModeloResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoPorModeloService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/votacoes")
public class VotacaoController {

    private final VotacaoService votacaoService;
    private final VotacaoPorModeloService votacaoPorModeloService;

    public VotacaoController(
            VotacaoService votacaoService, VotacaoPorModeloService votacaoPorModeloService) {
        this.votacaoService = votacaoService;
        this.votacaoPorModeloService = votacaoPorModeloService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VotacaoResponse criar(
            @RequestBody @Valid CriarVotacaoRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID administradorId =
                UUID.fromString(
                        jwt.getSubject()); // pegar quem é a pessoa que está fazendo a requisição
        return VotacaoResponse.of(votacaoService.criarVotacao(request, administradorId));
    }

    /** Modelos prontos para a tela de criação (RF-16); só exige usuário autenticado. */
    @GetMapping("/modelos")
    public List<ModeloVotacaoResponse> listarModelos() {
        return Arrays.stream(ModeloVotacao.values()).map(ModeloVotacaoResponse::of).toList();
    }

    /**
     * Cria a votação em rascunho já com regras e opções do modelo (RF-10, RF-16). Como na criação
     * comum, quem cria vira {@code ADMIN_VOTACAO}; regras e opções seguem editáveis em rascunho
     * pelos endpoints de RF-11 e RF-12.
     */
    @PostMapping("/por-modelo")
    @ResponseStatus(HttpStatus.CREATED)
    public VotacaoPorModeloResponse criarPorModelo(
            @RequestBody @Valid CriarVotacaoPorModeloRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Token JWT inválido: subject ausente");
        }

        return votacaoPorModeloService.criar(request, UUID.fromString(subject));
    }
}
