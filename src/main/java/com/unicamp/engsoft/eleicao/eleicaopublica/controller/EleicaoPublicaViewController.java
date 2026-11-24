package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.SituacaoEleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TipoConteudoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.service.EleicaoPublicaService;
import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Telas do módulo de eleições públicas (RF-30, RF-31, RF-33). Reusam o mesmo {@link
 * EleicaoPublicaService} da API, então o conteúdo das telas e o do JSON nunca divergem.
 */
@Controller
@RequestMapping("/eleicoes-publicas")
public class EleicaoPublicaViewController {

    private final EleicaoPublicaService service;

    public EleicaoPublicaViewController(EleicaoPublicaService service) {
        this.service = service;
    }

    @GetMapping
    public String eleicoes(
            @RequestParam(required = false) SituacaoEleicaoPublica situacao,
            @RequestParam(required = false) String cargo,
            Model model) {
        model.addAttribute("eleicoes", service.listarEleicoes(situacao, cargo));
        model.addAttribute("situacaoSelecionada", situacao);
        model.addAttribute("cargoFiltro", cargo);
        model.addAttribute("situacoes", SituacaoEleicaoPublica.values());
        return "eleicoes-publicas/eleicoes";
    }

    @GetMapping("/{eleicaoId}")
    public String candidatos(@PathVariable UUID eleicaoId, Model model) {
        model.addAttribute("dados", buscarOu404(() -> service.listarCandidatosPorCargo(eleicaoId)));
        return "eleicoes-publicas/candidatos";
    }

    @GetMapping("/candidatos/{candidatoId}")
    public String perfil(@PathVariable UUID candidatoId, Model model) {
        model.addAttribute("perfil", buscarOu404(() -> service.perfilDoCandidato(candidatoId)));
        return "eleicoes-publicas/perfil";
    }

    @GetMapping("/{eleicaoId}/comparar")
    public String comparar(
            @PathVariable UUID eleicaoId,
            @RequestParam(name = "candidatos", required = false) List<UUID> candidatosSelecionados,
            Model model) {
        var dados = buscarOu404(() -> service.listarCandidatosPorCargo(eleicaoId));
        model.addAttribute("dados", dados);
        model.addAttribute(
                "candidatosSelecionados",
                candidatosSelecionados == null ? List.of() : candidatosSelecionados);
        if (candidatosSelecionados != null && !candidatosSelecionados.isEmpty()) {
            try {
                model.addAttribute(
                        "comparacao",
                        buscarOu404(
                                () ->
                                        service.compararPropostas(
                                                eleicaoId, candidatosSelecionados)));
            } catch (DomainException ex) {
                model.addAttribute("erro", ex.getMessage());
            }
        }
        return "eleicoes-publicas/comparar";
    }

    @GetMapping("/{eleicaoId}/conteudos")
    public String conteudos(
            @PathVariable UUID eleicaoId,
            @RequestParam(required = false) TipoConteudoPublico tipo,
            @RequestParam(required = false) UUID candidatoId,
            @RequestParam(required = false) String tema,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        model.addAttribute("tipoSelecionado", tipo);
        model.addAttribute("candidatoIdSelecionado", candidatoId);
        model.addAttribute("temaSelecionado", tema);
        model.addAttribute("tiposConteudo", TipoConteudoPublico.values());
        try {
            model.addAttribute(
                    "dados",
                    buscarOu404(
                            () ->
                                    service.listarConteudos(
                                            eleicaoId, tipo, candidatoId, tema, page, size)));
        } catch (DomainException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute(
                    "dados",
                    buscarOu404(() -> service.listarConteudos(eleicaoId, null, null, null, 0, 10)));
        }
        return "eleicoes-publicas/conteudos";
    }

    /**
     * O {@code GlobalExceptionHandler} só atende {@code @RestController}; aqui o 404 vira {@link
     * ResponseStatusException} para cair na página {@code error.html} em vez de um 500.
     */
    private static <T> T buscarOu404(Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (RecursoNaoEncontradoException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
}
