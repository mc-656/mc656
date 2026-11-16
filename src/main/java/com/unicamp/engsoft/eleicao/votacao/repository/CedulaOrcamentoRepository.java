package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.CedulaOrcamento;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Acesso às cédulas de orçamento participativo (RF-21, append-only). */
public interface CedulaOrcamentoRepository extends Repository<CedulaOrcamento, UUID> {

    CedulaOrcamento save(CedulaOrcamento cedula);

    boolean existsByIdAndVotacaoId(UUID id, UUID votacaoId);
}
