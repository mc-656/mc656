package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Compromisso ou aparição pública na agenda de um candidato (RF-31, #132). */
@Entity
@Table(name = "aparicoes_candidato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AparicaoCandidato {

    @Id private UUID id;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(nullable = false, length = 200)
    private String local;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Embedded private Fonte fonte;
}
