package com.unicamp.engsoft.eleicao.usuario.controller;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.dto.CadastroUsuarioRequest;
import com.unicamp.engsoft.eleicao.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/api/usuarios")
    public Usuario postMethodName(@RequestBody @Valid CadastroUsuarioRequest req) {

        return usuarioService.criarUsuario(req);
    }
}
