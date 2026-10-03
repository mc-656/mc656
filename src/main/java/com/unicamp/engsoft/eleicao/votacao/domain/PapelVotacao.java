package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Atribuição de um papel a um usuário em uma votação (RF-03).
 *
 * <p>Referencia usuário e votação só pelo id: as checagens de autorização precisam apenas saber se
 * a linha existe, sem carregar as entidades. A remoção em cascata fica a cargo das FKs da V3.
 */
@Entity
@Table(name = "papeis_votacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PapelVotacao {

    @EmbeddedId private PapelVotacaoId id;

    public PapelVotacao(UUID usuarioId, UUID votacaoId, PapelUsuario papel) {
        this.id = new PapelVotacaoId(usuarioId, votacaoId, papel);
    }
}
