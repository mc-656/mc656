package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CargoAnterior;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoAnteriorRepository extends JpaRepository<CargoAnterior, UUID> {

    List<CargoAnterior> findByCandidatoIdOrderByAnoInicioDesc(UUID candidatoId);
}
