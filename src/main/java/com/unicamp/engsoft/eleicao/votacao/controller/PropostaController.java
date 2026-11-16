package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.DefinirOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.OrcamentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaSalvaResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostasResponse;
import com.unicamp.engsoft.eleicao.votacao.service.PropostaService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Propostas e orçamento total do orçamento participativo (RF-20). Escrita só para {@code
 * ADMIN_VOTACAO}; a listagem também é aberta ao {@code ELEITOR}, que precisa saber entre o que vai
 * distribuir pontos (RF-21) e quanto cada proposta custa.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}")
public class PropostaController {

    private final PropostaService propostaService;

    public PropostaController(PropostaService propostaService) {
        this.propostaService = propostaService;
    }

    @PostMapping("/propostas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public PropostaSalvaResponse cadastrar(
            @PathVariable UUID votacaoId, @RequestBody @Valid PropostaRequest request) {
        return propostaService.cadastrar(votacaoId, request);
    }

    @GetMapping("/propostas")
    @PreAuthorize(
            "@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)"
                    + " or @autorizacaoVotacao.ehEleitor(#votacaoId, authentication)")
    public PropostasResponse listar(@PathVariable UUID votacaoId) {
        return propostaService.listar(votacaoId);
    }

    @PutMapping("/propostas/{propostaId}")
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public PropostaSalvaResponse editar(
            @PathVariable UUID votacaoId,
            @PathVariable UUID propostaId,
            @RequestBody @Valid PropostaRequest request) {
        return propostaService.editar(votacaoId, propostaId, request);
    }

    @DeleteMapping("/propostas/{propostaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public void remover(@PathVariable UUID votacaoId, @PathVariable UUID propostaId) {
        propostaService.remover(votacaoId, propostaId);
    }

    @PutMapping("/orcamento")
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public OrcamentoResponse definirOrcamento(
            @PathVariable UUID votacaoId, @RequestBody @Valid DefinirOrcamentoRequest request) {
        return new OrcamentoResponse(propostaService.definirOrcamento(votacaoId, request));
    }
}
