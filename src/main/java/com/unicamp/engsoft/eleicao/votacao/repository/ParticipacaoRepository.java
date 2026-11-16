package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.Participacao;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Acesso às participações (RF-13). Estende {@link Repository}, e não {@code JpaRepository}, de
 * propósito: só declara inserção e consultas, então não existe {@code delete} nem {@code save} de
 * entidade já persistida para chamar por engano (append-only, SPECS §7.3).
 */
public interface ParticipacaoRepository extends Repository<Participacao, UUID> {

    /** Insere a participação e já sincroniza, para a {@code UNIQUE} estourar dentro do service. */
    Participacao saveAndFlush(Participacao participacao);

    boolean existsByUsuarioIdAndVotacaoId(UUID usuarioId, UUID votacaoId);
}
