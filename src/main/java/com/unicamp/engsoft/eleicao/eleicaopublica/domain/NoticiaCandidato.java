package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Notícia ou resumo de debate relacionado ao candidato (RF-33). A {@link Fonte} identifica o
 * veículo; {@code publicadaEm} é a data da matéria, distinta da data em que o registro foi
 * atualizado aqui.
 */
@Entity
@Table(name = "noticias_candidato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticiaCandidato {

    @Id private UUID id;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;

    @Column(nullable = false, length = 250)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "publicada_em", nullable = false)
    private LocalDate publicadaEm;

    @Embedded private Fonte fonte;
}
