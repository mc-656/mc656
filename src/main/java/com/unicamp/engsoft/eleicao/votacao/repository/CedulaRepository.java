package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.Cedula;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Acesso às cédulas (RF-13). Como {@link ParticipacaoRepository}, estende {@link Repository} para
 * não herdar {@code delete}: a cédula é append-only (SPECS §7.3).
 *
 * <p>Não há busca por usuário porque a cédula não tem usuário (RNF-02). Consultas para a apuração
 * (#50) entram junto com ela, de preferência já agregadas.
 */
public interface CedulaRepository extends Repository<Cedula, UUID> {

    Cedula save(Cedula cedula);

    /** Usada na verificação do comprovante: confirma que a cédula atestada foi de fato gravada. */
    boolean existsByIdAndVotacaoId(UUID id, UUID votacaoId);
}
