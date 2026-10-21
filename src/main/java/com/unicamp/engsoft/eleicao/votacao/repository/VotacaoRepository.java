package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotacaoRepository extends JpaRepository<Votacao, UUID> {

    /**
     * Carrega a votação com lock de escrita, para transições concorrentes (job de abertura e admin
     * cancelando, por exemplo) serem serializadas: a segunda vê o estado já alterado pela primeira.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Votacao v where v.id = :id")
    Optional<Votacao> buscarParaTransicao(@Param("id") UUID id);

    @Query("select v.id from Votacao v where v.estado = :estado and v.inicioEm <= :instante")
    List<UUID> idsPorEstadoComInicioAte(
            @Param("estado") EstadoVotacao estado, @Param("instante") Instant instante);
}
