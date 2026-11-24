package com.unicamp.engsoft.eleicao.votacao.repository;

import com.unicamp.engsoft.eleicao.votacao.domain.AlocacaoOrcamento;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Acesso às alocações de pontos de propostas de orçamento participativo (RF-21, append-only). */
public interface AlocacaoOrcamentoRepository extends Repository<AlocacaoOrcamento, UUID> {

    AlocacaoOrcamento save(AlocacaoOrcamento alocacao);

    List<AlocacaoOrcamento> findByCedulaId(UUID cedulaId);

    List<AlocacaoOrcamento> findByCedulaIdIn(Collection<UUID> cedulaIds);
}
