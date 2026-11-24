package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConteudoPublicoCandidatoId implements Serializable {

    @Column(name = "conteudo_id", nullable = false, updatable = false)
    private UUID conteudoId;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;
}
