package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.SaldoOrcamentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotoOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.service.RegistroVotoOrcamentoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de votação de Orçamento Participativo (RF-21, RF-22) e consulta de saldo. */
@RestController
public class VotoOrcamentoController {

    private final RegistroVotoOrcamentoService registroVotoOrcamentoService;

    public VotoOrcamentoController(RegistroVotoOrcamentoService registroVotoOrcamentoService) {
        this.registroVotoOrcamentoService = registroVotoOrcamentoService;
    }

    /** Consulta o saldo total e disponível de pontos do eleitor na votação (RF-21). */
    @GetMapping("/api/votacoes/{votacaoId}/orcamento/saldo")
    @PreAuthorize("@autorizacaoVotacao.ehEleitor(#votacaoId, authentication)")
    public SaldoOrcamentoResponse consultarSaldo(
            @PathVariable UUID votacaoId, Authentication autenticacao) {
        return registroVotoOrcamentoService.consultarSaldo(votacaoId, autenticacao);
    }

    /**
     * Registra a distribuição de pontos entre as propostas e emite comprovante criptográfico
     * (RF-21, RF-22).
     */
    @PostMapping("/api/votacoes/{votacaoId}/votos-orcamento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizacaoVotacao.ehEleitor(#votacaoId, authentication)")
    public ComprovanteResponse votar(
            @PathVariable UUID votacaoId,
            @RequestBody @Valid VotoOrcamentoRequest request,
            Authentication autenticacao) {
        return registroVotoOrcamentoService.registrar(votacaoId, request, autenticacao);
    }
}
