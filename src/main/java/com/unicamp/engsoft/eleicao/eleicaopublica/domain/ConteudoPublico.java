package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Notícia ou resumo de debate vinculado a uma eleição pública (RF-33, #133, #135). */
@Entity
@Table(name = "conteudos_publicos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConteudoPublico {

    @Id private UUID id;

    @Column(name = "eleicao_id", nullable = false, updatable = false)
    private UUID eleicaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoConteudoPublico tipo;

    @Column(nullable = false, length = 250)
    private String titulo;

    @Column(length = 60)
    private String tema;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "publicada_em", nullable = false)
    private LocalDate publicadaEm;

    @Embedded private Fonte fonte;
}
