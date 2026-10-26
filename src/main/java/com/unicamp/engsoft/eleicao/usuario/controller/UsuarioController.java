package com.unicamp.engsoft.eleicao.usuario.controller;

import com.unicamp.engsoft.eleicao.usuario.dto.CadastroUsuarioRequest;
import com.unicamp.engsoft.eleicao.usuario.dto.UsuarioResponse;
import com.unicamp.engsoft.eleicao.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/api/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse postMethodName(@RequestBody @Valid CadastroUsuarioRequest req) {
        return UsuarioResponse.de(usuarioService.criarUsuario(req));
    }
}
