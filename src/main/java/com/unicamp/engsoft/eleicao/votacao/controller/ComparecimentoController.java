package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.ComparecimentoResponse;
import com.unicamp.engsoft.eleicao.votacao.service.ComparecimentoService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta em tempo real de comparecimento e quórum durante {@code EmVotacao}/{@code Suspensa}
 * (RF-40). Acessível ao administrador e aos eleitores da votação; não expõe contagens por opção
 * (RNF-02).
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/comparecimento")
public class ComparecimentoController {

    private final ComparecimentoService comparecimentoService;

    public ComparecimentoController(ComparecimentoService comparecimentoService) {
        this.comparecimentoService = comparecimentoService;
    }

    @GetMapping
    @PreAuthorize("@autorizacaoVotacao.ehParticipante(#votacaoId, authentication)")
    public ComparecimentoResponse consultar(@PathVariable UUID votacaoId) {
        return comparecimentoService.consultar(votacaoId);
    }
}
