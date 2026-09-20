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

@Entity
@Table(name = "votacoes")
public class Votacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoVotacao tipo;

    @Column(name = "inicio_em", nullable = false)
    private Instant inicioEm;

    @Column(name = "fim_em", nullable = false)
    private Instant fimEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoVotacao estado = EstadoVotacao.RascunhoVotacao;

    protected Votacao() {}

    public Votacao(TipoVotacao tipo, Instant inicioEm, Instant fimEm) {
        this.tipo = tipo;
        this.inicioEm = inicioEm;
        this.fimEm = fimEm;
    }

    public UUID getId() {
        return id;
    }

    public TipoVotacao getTipo() {
        return tipo;
    }

    public Instant getInicioEm() {
        return inicioEm;
    }

    public Instant getFimEm() {
        return fimEm;
    }

    public EstadoVotacao getEstado() {
        return estado;
    }
}