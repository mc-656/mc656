package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropostaRepository extends JpaRepository<Proposta, UUID> {

    List<Proposta> findByVotacaoIdOrderByCriadoEm(UUID votacaoId);

    Optional<Proposta> findByIdAndVotacaoId(UUID id, UUID votacaoId);

    long countByVotacaoId(UUID votacaoId);

    boolean existsByVotacaoIdAndTituloIgnoreCase(UUID votacaoId, String titulo);

    boolean existsByVotacaoIdAndTituloIgnoreCaseAndIdNot(UUID votacaoId, String titulo, UUID id);

    /** Maior custo entre as propostas da votação, ou {@code null} se ela não tem nenhuma. */
    @Query("select max(p.custo) from Proposta p where p.votacaoId = :votacaoId")
    BigDecimal maiorCusto(@Param("votacaoId") UUID votacaoId);
}
