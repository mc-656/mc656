package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Alocação de pontos a uma proposta específica de orçamento participativo (RF-21, RF-22).
 *
 * <p>Também imutável (append-only) e sem nenhuma ligação a eleitor ou participação (RNF-02).
 */
@Entity
@Table(name = "alocacoes_orcamento")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlocacaoOrcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cedula_id", nullable = false, updatable = false)
    private UUID cedulaId;

    @Column(name = "proposta_id", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal pontos;

    public AlocacaoOrcamento(UUID cedulaId, UUID propostaId, BigDecimal pontos) {
        this.cedulaId = cedulaId;
        this.propostaId = propostaId;
        this.pontos = pontos;
    }
}
