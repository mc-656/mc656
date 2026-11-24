package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TopicoDebate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicoDebateRepository extends JpaRepository<TopicoDebate, UUID> {

    List<TopicoDebate> findByConteudoIdInOrderByTemaAscOrdemAsc(Collection<UUID> conteudoIds);
}
