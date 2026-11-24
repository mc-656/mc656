package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Cédula de uma votação de orçamento participativo (RF-21, RNF-02).
 *
 * <p>Assim como a {@link Cedula}, não contém referência ao eleitor nem horário de criação,
 * garantindo o sigilo do voto mesmo contra leituras diretas no banco de dados.
 */
@Entity
@Table(name = "cedulas_orcamento")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CedulaOrcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    public CedulaOrcamento(UUID votacaoId) {
        this.votacaoId = votacaoId;
    }
}
