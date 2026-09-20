package com.unicamp.engsoft.eleicao.usuario.repository;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsername(String username);
}