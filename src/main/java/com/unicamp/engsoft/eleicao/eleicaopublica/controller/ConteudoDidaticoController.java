package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.TiposVotoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.service.ConteudoDidaticoService;
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
    private final ConteudoDidaticoService conteudoDidatico;

    public ConteudoDidaticoController(
            ExplicacaoQuocienteEleitoralService quocienteEleitoral,
            ConteudoDidaticoService conteudoDidatico) {
        this.quocienteEleitoral = quocienteEleitoral;
        this.conteudoDidatico = conteudoDidatico;
    }

    @GetMapping("/quociente-eleitoral")
    public ExplicacaoQuocienteEleitoralResponse quocienteEleitoral() {
        return quocienteEleitoral.explicar();
    }

    /** Voto nulo, em branco e na legenda (#91). */
    @GetMapping("/tipos-voto")
    public TiposVotoResponse tiposVoto() {
        return conteudoDidatico.tiposVoto();
    }

    /**
     * Cargos e chapas (#92). Cada cargo traz {@code id} e {@code urlDescricao} estáveis para que o
     * perfil do candidato aponte para a descrição do cargo que ele disputa.
     */
    @GetMapping("/cargos")
    public CargosResponse cargos() {
        return conteudoDidatico.cargos();
    }
}
