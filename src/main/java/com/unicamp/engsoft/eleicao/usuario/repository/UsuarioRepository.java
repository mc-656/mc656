package com.unicamp.engsoft.eleicao.usuario.repository;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    boolean existsByCpf(String cpf);

    boolean existsByEmail(String email);

    Optional<Usuario> findByCpf(String cpf);
}
