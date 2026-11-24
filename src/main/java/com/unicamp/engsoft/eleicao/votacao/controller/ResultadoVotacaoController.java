package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.ResultadoVotacaoService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoint de consulta do resultado oficial ou anulação de uma votação (RF-41, RF-42). */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/resultado")
public class ResultadoVotacaoController {

    private final ResultadoVotacaoService resultadoService;

    public ResultadoVotacaoController(ResultadoVotacaoService resultadoService) {
        this.resultadoService = resultadoService;
    }

    @GetMapping
    @PreAuthorize(
            "@autorizacaoVotacao.ehAdmin(#votacaoId, authentication) or"
                    + " @autorizacaoVotacao.ehEleitor(#votacaoId, authentication)")
    public ResultadoVotacaoResponse consultar(
            @PathVariable UUID votacaoId, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT não encontrado");
        }
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Token JWT sem identificação do usuário");
        }
        return resultadoService.consultarResultado(votacaoId, UUID.fromString(subject));
    }
}
