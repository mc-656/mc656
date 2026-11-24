package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.ConteudoPublicoCandidato;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.ConteudoPublicoCandidatoId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConteudoPublicoCandidatoRepository
        extends JpaRepository<ConteudoPublicoCandidato, ConteudoPublicoCandidatoId> {

    List<ConteudoPublicoCandidato> findByIdConteudoIdIn(Collection<UUID> conteudoIds);
}
