package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Associação N:N entre conteúdo público e candidatos mencionados (RF-33). */
@Entity
@Table(name = "conteudos_publicos_candidatos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConteudoPublicoCandidato {

    @EmbeddedId private ConteudoPublicoCandidatoId id;
}
