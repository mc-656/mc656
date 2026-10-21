package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatoOuChapaRepository extends JpaRepository<CandidatoOuChapa, UUID> {

    List<CandidatoOuChapa> findByVotacaoIdOrderByCriadoEm(UUID votacaoId);

    Optional<CandidatoOuChapa> findByIdAndVotacaoId(UUID id, UUID votacaoId);

    long countByVotacaoId(UUID votacaoId);

    boolean existsByVotacaoIdAndNomeIgnoreCase(UUID votacaoId, String nome);
}
