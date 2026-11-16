package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cargo já ocupado pelo candidato (histórico, RF-31). {@code anoFim} nulo = mandato em curso. */
@Entity
@Table(name = "cargos_anteriores_candidato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CargoAnterior {

    @Id private UUID id;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;

    @Column(nullable = false, length = 150)
    private String cargo;

    @Column(name = "ano_inicio", nullable = false)
    private int anoInicio;

    @Column(name = "ano_fim")
    private Integer anoFim;

    @Embedded private Fonte fonte;
}
