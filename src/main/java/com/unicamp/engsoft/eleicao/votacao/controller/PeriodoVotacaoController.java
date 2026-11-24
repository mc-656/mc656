package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.DefinirPeriodoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PeriodoVotacaoController {
    private final VotacaoService votacoes;

    public PeriodoVotacaoController(VotacaoService votacoes) {
        this.votacoes = votacoes;
    }

    @PutMapping("/api/votacoes/{votacaoId}/periodo")
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public VotacaoResponse definir(
            @PathVariable UUID votacaoId, @RequestBody @Valid DefinirPeriodoRequest request) {
        return VotacaoResponse.of(
                votacoes.definirPeriodo(votacaoId, request.inicioEm(), request.fimEm()));
    }
}
