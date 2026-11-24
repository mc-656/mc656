package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.Apuracao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Sem operações de atualização ou remoção de snapshots. */
public interface ApuracaoRepository extends Repository<Apuracao, UUID> {
    Apuracao save(Apuracao apuracao);

    Optional<Apuracao> findById(UUID votacaoId);

    boolean existsByVotacaoId(UUID votacaoId);
}
