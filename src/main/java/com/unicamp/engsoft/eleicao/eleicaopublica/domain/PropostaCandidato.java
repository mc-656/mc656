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

/**
 * Proposta do plano de governo (RF-31). O {@code tema} é texto livre e serve para agrupar as
 * propostas no perfil, já que o plano registrado no TSE não segue taxonomia oficial.
 */
@Entity
@Table(name = "propostas_candidato")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PropostaCandidato {

    @Id private UUID id;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;

    @Column(nullable = false, length = 60)
    private String tema;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Embedded private Fonte fonte;
}
