package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de auditoria de uma transição de estado de {@link Votacao} (RNF-03): quem, quando, de
 * qual estado para qual e por quê.
 *
 * <p>Append-only: sem setters, nunca atualizado nem removido. O autor é nulo exatamente quando a
 * origem é {@link OrigemTransicao#SISTEMA}; a V7 garante isso com uma CHECK.
 */
@Entity
@Table(name = "transicoes_votacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransicaoVotacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 30, updatable = false)
    private EstadoVotacao estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_novo", nullable = false, length = 30, updatable = false)
    private EstadoVotacao estadoNovo;

    @Column(name = "autor_id", updatable = false)
    private UUID autorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private OrigemTransicao origem;

    @Column(length = 500, updatable = false)
    private String motivo;

    @Column(name = "ocorrida_em", nullable = false, updatable = false)
    private Instant ocorridaEm;

    public TransicaoVotacao(
            UUID votacaoId,
            EstadoVotacao estadoAnterior,
            EstadoVotacao estadoNovo,
            UUID autorId,
            String motivo,
            Instant ocorridaEm) {
        this.votacaoId = votacaoId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNovo = estadoNovo;
        this.autorId = autorId;
        this.origem = autorId == null ? OrigemTransicao.SISTEMA : OrigemTransicao.USUARIO;
        this.motivo = motivo;
        this.ocorridaEm = ocorridaEm;
    }
}
