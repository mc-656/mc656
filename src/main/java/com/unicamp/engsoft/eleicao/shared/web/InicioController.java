package com.unicamp.engsoft.eleicao.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Página inicial após o login. Placeholder até as telas de votação (#67). */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }
}
