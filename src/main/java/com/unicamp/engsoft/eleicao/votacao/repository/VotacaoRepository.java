package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotacaoRepository extends JpaRepository<Votacao, UUID> {}
