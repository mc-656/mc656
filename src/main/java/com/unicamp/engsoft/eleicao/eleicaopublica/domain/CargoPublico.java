package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cargo em disputa numa eleição pública (ex.: Prefeito, Vereador). A {@code ordem} define a
 * sequência de exibição, que segue a da urna e não a alfabética.
 */
@Entity
@Table(name = "cargos_publicos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CargoPublico {

    @Id private UUID id;

    @Column(name = "eleicao_id", nullable = false, updatable = false)
    private UUID eleicaoId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private int ordem;

    /**
     * Identificador do cargo no conteúdo didático (RF-32): nome sem acentos, minúsculo e com hífens
     * ("Deputado Federal" vira {@code deputado-federal}). Derivado em vez de guardado em coluna
     * porque o didático já usa essa convenção e uma coluna a mais só poderia divergir dela.
     */
    public String idDidatico() {
        return Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
