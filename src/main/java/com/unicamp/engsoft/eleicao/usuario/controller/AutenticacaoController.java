package com.unicamp.engsoft.eleicao.usuario.controller;

import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.dto.LoginRequest;
import com.unicamp.engsoft.eleicao.usuario.dto.LoginResponse;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Troca credenciais por um JWT (RF-03). Único endpoint público além do cadastro. */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AutenticacaoController(
            AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest req) {
        Authentication autenticacao =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(req.email(), req.senha()));

        return new LoginResponse(
                tokenService.gerar((UsuarioAutenticado) autenticacao.getPrincipal()), "Bearer");
    }
}
