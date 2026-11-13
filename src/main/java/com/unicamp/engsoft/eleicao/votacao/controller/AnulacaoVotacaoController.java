package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.AnulacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.service.AnulacaoVotacaoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/votacoes/{votacaoId}/anulacao")
public class AnulacaoVotacaoController {

    private final AnulacaoVotacaoService anulacaoService;

    public AnulacaoVotacaoController(AnulacaoVotacaoService anulacaoService) {
        this.anulacaoService = anulacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public void anular(
            @PathVariable UUID votacaoId,
            @RequestBody @Valid AnulacaoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT não encontrado");
        }

        String subject = jwt.getSubject();

        if (subject == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Token JWT sem identificação do usuário");
        }

        UUID usuarioId = UUID.fromString(subject);

        anulacaoService.anularPorIrregularidade(votacaoId, usuarioId, request.justificativa());
    }
}
