package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.EleicaoPublica;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EleicaoPublicaRepository extends JpaRepository<EleicaoPublica, UUID> {

    /** Mais recentes primeiro: a eleição que vem aí interessa mais que as já ocorridas. */
    List<EleicaoPublica> findAllByOrderByDataPrimeiroTurnoDescNomeAsc();
}
