package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.ConteudoPublico;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConteudoPublicoRepository extends JpaRepository<ConteudoPublico, UUID> {

    List<ConteudoPublico> findByEleicaoIdOrderByPublicadaEmDescTituloAsc(UUID eleicaoId);
}
