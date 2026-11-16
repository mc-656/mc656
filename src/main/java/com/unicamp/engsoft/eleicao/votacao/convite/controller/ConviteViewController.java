package com.unicamp.engsoft.eleicao.votacao.convite.controller;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.convite.service.ConviteService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Destino do link de convite (RF-17) — é esta URL que vai no e-mail, no WhatsApp e no QR Code.
 *
 * <p>A rota está na cadeia das views, que exige sessão: quem chega sem login é mandado para {@code
 * /login} pelo próprio Spring Security, que guarda a requisição original (saved request) e volta a
 * ela depois do login — inclusive passando pelo cadastro antes, já que a sessão é a mesma. Por isso
 * o {@code GET} já inclui o eleitor: é a única requisição que sobrevive a esse desvio.
 *
 * <p>Erros de domínio (restrito fora da lista, votação fora da janela, token inválido) viram a
 * mesma página com a mensagem e o status HTTP da exceção, em vez da página de erro genérica.
 */
@Controller
public class ConviteViewController {

    private final ConviteService conviteService;

    public ConviteViewController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    @GetMapping("/convites/{token}")
    public String abrir(
            @PathVariable String token,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model,
            HttpServletResponse response) {
        try {
            model.addAttribute("aceito", conviteService.aceitar(token, usuario.getId()));
        } catch (DomainException e) {
            response.setStatus(e.getStatus().value());
            model.addAttribute("erro", e.getMessage());
        }
        return "convite";
    }
}
