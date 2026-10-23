package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.RegraVotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração das regras de apuração de uma votação (RF-11). Escrita só para {@code
 * ADMIN_VOTACAO}, e apenas enquanto a votação está em rascunho — a checagem de estado fica no
 * serviço, junto das outras regras de negócio.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/regras")
public class RegraVotacaoController {

    private final VotacaoService votacaoService;

    public RegraVotacaoController(VotacaoService votacaoService) {
        this.votacaoService = votacaoService;
    }

    @PutMapping
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public RegraVotacaoResponse configurar(
            @PathVariable UUID votacaoId, @RequestBody @Valid ConfigurarRegrasRequest request) {
        return RegraVotacaoResponse.of(votacaoService.configurarRegras(votacaoId, request));
    }
}
