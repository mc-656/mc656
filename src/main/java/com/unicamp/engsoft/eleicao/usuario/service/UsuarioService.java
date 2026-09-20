package com.unicamp.engsoft.eleicao.usuario.service;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Usuario criarUsuario(Usuario usuario) {
        if (usuarioRepository.existsByCpf(usuario.getCpf())) {
            throw new RegraDeNegocioException("CPF já cadastrado");
        }

        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new RegraDeNegocioException("Email já cadastrado");
        }

        return usuarioRepository.save(usuario);
    }
}
