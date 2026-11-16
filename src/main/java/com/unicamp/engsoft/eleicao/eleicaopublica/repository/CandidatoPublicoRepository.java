package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoPublico;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidatoPublicoRepository extends JpaRepository<CandidatoPublico, UUID> {

    /** Uma consulta para todos os cargos da eleição, já com o cargo carregado (evita N+1). */
    @Query(
            """
            SELECT c FROM CandidatoPublico c JOIN FETCH c.cargo cargo
            WHERE cargo.eleicaoId = :eleicaoId
            ORDER BY cargo.ordem, c.nomeUrna
            """)
    List<CandidatoPublico> buscarPorEleicao(@Param("eleicaoId") UUID eleicaoId);

    @Query("SELECT c FROM CandidatoPublico c JOIN FETCH c.cargo WHERE c.id = :id")
    Optional<CandidatoPublico> buscarComCargo(@Param("id") UUID id);
}
