package com.unicamp.engsoft.eleicao.usuario.controller;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.usuario.dto.CadastroUsuarioRequest;
import com.unicamp.engsoft.eleicao.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Telas de login e cadastro (RF-01/RF-03).
 *
 * <p>O {@code POST /login} não passa por aqui: quem processa é o form login do Spring Security
 * ({@code SecurityConfig}). Este controller só renderiza o formulário.
 *
 * <p>O cadastro reusa o {@link CadastroUsuarioRequest} e o {@link UsuarioService} da API, então
 * validação e regras de unicidade são as mesmas nos dois caminhos.
 */
@Controller
public class UsuarioViewController {

    private static final String CADASTRO = "cadastro";

    private final UsuarioService usuarioService;

    public UsuarioViewController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/cadastro")
    public String formularioCadastro(Model model) {
        model.addAttribute(CADASTRO, new CadastroUsuarioRequest(null, null, null, null));
        return CADASTRO;
    }

    /**
     * Erro de domínio (e-mail ou CPF repetido) vira erro global do formulário, com a mensagem do
     * próprio domínio. Sucesso redireciona (PRG) para o login, evitando reenvio no F5.
     */
    @PostMapping("/cadastro")
    public String cadastrar(
            @Valid @ModelAttribute(CADASTRO) CadastroUsuarioRequest req, BindingResult erros) {
        if (erros.hasErrors()) {
            return CADASTRO;
        }
        try {
            usuarioService.criarUsuario(req);
        } catch (DomainException ex) {
            erros.reject("cadastro.recusado", ex.getMessage());
            return CADASTRO;
        }
        return "redirect:/login?cadastrado";
    }
}
