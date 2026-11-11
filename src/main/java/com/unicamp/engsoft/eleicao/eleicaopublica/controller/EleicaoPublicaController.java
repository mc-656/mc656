package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CandidatosDaEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.EleicaoPublicaResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.PerfilCandidatoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.service.EleicaoPublicaService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eleições públicas, candidatos e perfil (RF-30, RF-31, RF-33). Rotas públicas e só de leitura: ver
 * {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/eleicoes-publicas")
public class EleicaoPublicaController {

    private final EleicaoPublicaService service;

    public EleicaoPublicaController(EleicaoPublicaService service) {
        this.service = service;
    }

    @GetMapping
    public List<EleicaoPublicaResponse> listar() {
        return service.listarEleicoes();
    }

    @GetMapping("/{eleicaoId}/candidatos")
    public CandidatosDaEleicaoResponse candidatos(@PathVariable UUID eleicaoId) {
        return service.listarCandidatosPorCargo(eleicaoId);
    }

    @GetMapping("/candidatos/{candidatoId}")
    public PerfilCandidatoResponse perfil(@PathVariable UUID candidatoId) {
        return service.perfilDoCandidato(candidatoId);
    }
}
