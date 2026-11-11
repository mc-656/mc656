package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.PropostaCandidato;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropostaCandidatoRepository extends JpaRepository<PropostaCandidato, UUID> {

    List<PropostaCandidato> findByCandidatoIdOrderByTemaAscTituloAsc(UUID candidatoId);
}
