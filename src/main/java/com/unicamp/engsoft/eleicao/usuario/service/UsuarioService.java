package com.unicamp.engsoft.eleicao.usuario.service;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.dto.CadastroUsuarioRequest;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario criarUsuario(CadastroUsuarioRequest usuarioDto) {
        if (usuarioRepository.existsByEmail(usuarioDto.email()))
            throw new RegraDeNegocioException("Já existe um usuário com esse email.");
        // Em uma aplicação real,
        // poderíamos ter problemas porque
        // isso vaza a existência de um
        // email registrado. Mas nesse
        // escopo, vamos manter assim.

        if (usuarioRepository.existsByCpf(usuarioDto.cpf()))
            throw new RegraDeNegocioException("Já existe um usuário com esse CPF.");

        return usuarioRepository.save(
                usuarioDto.paraUsuario(passwordEncoder.encode(usuarioDto.senha())));
    }
}
