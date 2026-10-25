package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.service.ExplicacaoQuocienteEleitoralService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conteúdo didático sobre o sistema eleitoral (RF-32, RNF-07). Rotas públicas: ver {@code
 * SecurityConfig}.
 */
@RestController
@RequestMapping("/api/eleicoes-publicas/didatico")
public class ConteudoDidaticoController {

    private final ExplicacaoQuocienteEleitoralService quocienteEleitoral;

    public ConteudoDidaticoController(ExplicacaoQuocienteEleitoralService quocienteEleitoral) {
        this.quocienteEleitoral = quocienteEleitoral;
    }

    @GetMapping("/quociente-eleitoral")
    public ExplicacaoQuocienteEleitoralResponse quocienteEleitoral() {
        return quocienteEleitoral.explicar();
    }
}
