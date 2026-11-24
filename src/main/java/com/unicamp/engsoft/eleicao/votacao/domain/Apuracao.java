package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Snapshot único por votação. Não guarda usuários nem associa eleitores às cédulas. */
@Entity
@Immutable
@Table(name = "apuracoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Apuracao {
    @Id
    @Column(name = "votacao_id", updatable = false)
    private UUID votacaoId;

    @Column(name = "concluida_em", nullable = false, updatable = false)
    private Instant concluidaEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private RegraVotacao regra;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private ResultadoApuracao resultado;

    @Column(name = "total_cedulas", nullable = false, updatable = false)
    private long totalCedulas;

    @Column(name = "segundo_turno_id", updatable = false)
    private UUID segundoTurnoId;

    public Apuracao(
            UUID votacaoId,
            Instant concluidaEm,
            RegraVotacao regra,
            ResultadoApuracao resultado,
            long totalCedulas,
            UUID segundoTurnoId) {
        this.votacaoId = votacaoId;
        this.concluidaEm = concluidaEm;
        this.regra = regra;
        this.resultado = resultado;
        this.totalCedulas = totalCedulas;
        this.segundoTurnoId = segundoTurnoId;
    }

    public boolean quorumAtingido() {
        return resultado != null && resultado.quorumAtingido();
    }
}
