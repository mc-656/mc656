package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import com.unicamp.engsoft.eleicao.eleicaopublica.service.ConteudoDidaticoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Páginas públicas de conteúdo didático (RF-32, RNF-07): tipos de voto (#91) e cargos e chapas
 * (#92). Separado do {@link ConteudoDidaticoController} porque aquele é {@code @RestController} e
 * devolve JSON; aqui o retorno é o nome do template. Os dois leem do mesmo {@link
 * ConteudoDidaticoService}, então página e API mostram sempre o mesmo texto.
 */
@Controller
@RequestMapping("/eleicoes-publicas")
public class ConteudoDidaticoViewController {

    private final ConteudoDidaticoService conteudoDidatico;

    public ConteudoDidaticoViewController(ConteudoDidaticoService conteudoDidatico) {
        this.conteudoDidatico = conteudoDidatico;
    }

    @GetMapping("/tipos-voto")
    public String tiposVoto(Model model) {
        model.addAttribute("conteudo", conteudoDidatico.tiposVoto());
        return "eleicaopublica/tipos-voto";
    }

    @GetMapping("/cargos")
    public String cargos(Model model) {
        model.addAttribute("conteudo", conteudoDidatico.cargos());
        return "eleicaopublica/cargos";
    }
}
