package com.unicamp.engsoft.eleicao.votacao.convite.controller;

import com.unicamp.engsoft.eleicao.votacao.convite.dto.ConviteAceitoResponse;
import com.unicamp.engsoft.eleicao.votacao.convite.service.ConviteService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Entrada como eleitor pelo token do convite, para clientes da API (RF-17). Qualquer usuário
 * autenticado pode chamar: quem decide se ele entra é o modo do convite, não um papel prévio.
 *
 * <p>O link que vai no WhatsApp/QR Code aponta para a view ({@code GET /convites/{token}}), que
 * cuida do login com retorno; este endpoint é o mesmo fluxo para quem já tem o JWT.
 */
@RestController
@RequestMapping("/api/convites/{token}")
public class AceiteConviteController {

    private final ConviteService conviteService;

    public AceiteConviteController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    @PostMapping("/aceite")
    public ConviteAceitoResponse aceitar(
            @PathVariable String token, @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }

        String subject = jwt.getSubject();

        if (subject == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "JWT sem identificação do usuário");
        }

        UUID usuarioId = UUID.fromString(subject);

        return conviteService.aceitar(token, usuarioId);
    }
}
