package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.service.EleicaoPublicaService;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public String eleicoes(Model model) {
        model.addAttribute("eleicoes", service.listarEleicoes());
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
