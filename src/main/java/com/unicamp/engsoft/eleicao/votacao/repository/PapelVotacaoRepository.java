package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PapelVotacaoRepository extends JpaRepository<PapelVotacao, PapelVotacaoId> {
    boolean existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
            UUID usuarioId, UUID votacaoId, PapelUsuario papel);

    List<PapelVotacao> findByIdVotacaoId(UUID votacaoId);

    List<PapelVotacao> findByIdUsuarioId(UUID usuarioId);
}
