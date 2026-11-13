package com.unicamp.engsoft.eleicao.votacao.convite.repository;

import com.unicamp.engsoft.eleicao.votacao.convite.domain.ConviteVotacao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConviteVotacaoRepository extends JpaRepository<ConviteVotacao, UUID> {

    Optional<ConviteVotacao> findByToken(String token);
}
