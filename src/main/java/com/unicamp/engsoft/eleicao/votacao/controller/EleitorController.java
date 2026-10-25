package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.AdicionarEleitorRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.EleitorResponse;
import com.unicamp.engsoft.eleicao.votacao.service.EleitorService;
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
 * Eleitores elegíveis de uma votação (RF-02, RF-10). Tudo, inclusive a listagem, é só para {@code
 * ADMIN_VOTACAO}: a lista revela quem participa e com que peso, o que um eleitor não precisa ver.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/eleitores")
public class EleitorController {

    private final EleitorService eleitorService;

    public EleitorController(EleitorService eleitorService) {
        this.eleitorService = eleitorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public EleitorResponse adicionar(
            @PathVariable UUID votacaoId, @RequestBody @Valid AdicionarEleitorRequest request) {
        return eleitorService.adicionar(votacaoId, request);
    }

    @GetMapping
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public List<EleitorResponse> listar(@PathVariable UUID votacaoId) {
        return eleitorService.listar(votacaoId);
    }

    @DeleteMapping("/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public void remover(@PathVariable UUID votacaoId, @PathVariable UUID usuarioId) {
        eleitorService.remover(votacaoId, usuarioId);
    }
}
