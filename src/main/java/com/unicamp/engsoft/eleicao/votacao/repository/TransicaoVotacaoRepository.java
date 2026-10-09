package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransicaoVotacaoRepository extends JpaRepository<TransicaoVotacao, UUID> {

    List<TransicaoVotacao> findByVotacaoIdOrderByOcorridaEmAsc(UUID votacaoId);
}
