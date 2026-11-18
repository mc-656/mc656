package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tópico estruturado de um resumo de debate, limitado a 5 por tema na exibição (RF-33, #133). */
@Entity
@Table(name = "topicos_debate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicoDebate {

    @Id private UUID id;

    @Column(name = "conteudo_id", nullable = false, updatable = false)
    private UUID conteudoId;

    @Column(nullable = false, length = 60)
    private String tema;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumo;
}
