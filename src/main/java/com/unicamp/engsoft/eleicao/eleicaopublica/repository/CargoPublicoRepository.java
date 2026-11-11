package com.unicamp.engsoft.eleicao.eleicaopublica.repository;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CargoPublico;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoPublicoRepository extends JpaRepository<CargoPublico, UUID> {

    List<CargoPublico> findByEleicaoIdOrderByOrdemAscNomeAsc(UUID eleicaoId);
}
