package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.SituacaoEleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TipoConteudoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CandidatosDaEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.EleicaoPublicaResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.PerfilCandidatoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.service.EleicaoPublicaService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eleições públicas, candidatos, perfil, comparador e conteúdos (RF-30, RF-31, RF-33). Rotas
 * públicas e só de leitura: ver {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/eleicoes-publicas")
public class EleicaoPublicaController {

    private final EleicaoPublicaService service;

    public EleicaoPublicaController(EleicaoPublicaService service) {
        this.service = service;
    }

    @GetMapping
    public List<EleicaoPublicaResponse> listar(
            @RequestParam(required = false) SituacaoEleicaoPublica situacao,
            @RequestParam(required = false) String cargo) {
        return service.listarEleicoes(situacao, cargo);
    }

    @GetMapping("/{eleicaoId}/candidatos")
    public CandidatosDaEleicaoResponse candidatos(@PathVariable UUID eleicaoId) {
        return service.listarCandidatosPorCargo(eleicaoId);
    }

    @GetMapping("/candidatos/{candidatoId}")
    public PerfilCandidatoResponse perfil(@PathVariable UUID candidatoId) {
        return service.perfilDoCandidato(candidatoId);
    }

    @GetMapping("/{eleicaoId}/comparacao")
    public ComparacaoPropostasResponse comparar(
            @PathVariable UUID eleicaoId,
            @RequestParam(name = "candidatos") List<UUID> candidatos) {
        return service.compararPropostas(eleicaoId, candidatos);
    }

    @GetMapping("/{eleicaoId}/conteudos")
    public ConteudosEleicaoResponse conteudos(
            @PathVariable UUID eleicaoId,
            @RequestParam(required = false) TipoConteudoPublico tipo,
            @RequestParam(required = false) UUID candidatoId,
            @RequestParam(required = false) String tema,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.listarConteudos(eleicaoId, tipo, candidatoId, tema, page, size);
    }
}
