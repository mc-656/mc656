package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.NoticiaCandidato;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticiaCandidatoRepository extends JpaRepository<NoticiaCandidato, UUID> {

    List<NoticiaCandidato> findByCandidatoIdOrderByPublicadaEmDesc(UUID candidatoId);
}
