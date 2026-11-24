package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.AparicaoCandidato;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AparicaoCandidatoRepository extends JpaRepository<AparicaoCandidato, UUID> {

    List<AparicaoCandidato> findByCandidatoIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            UUID candidatoId, Instant aPartirDe);
}
