package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Atribuição de um papel a um usuário em uma votação (RF-03).
 *
 * <p>Referencia usuário e votação só pelo id: as checagens de autorização precisam apenas saber se
 * a linha existe, sem carregar as entidades. A remoção em cascata fica a cargo das FKs da V3.
 *
 * <p>O {@code peso} do voto ponderado mora aqui, no papel {@code ELEITOR}, e não em {@link
 * RegraVotacao}: é um dado de cada eleitor, e a cédula (#48) copia o peso daqui no momento do voto.
 * Nos papéis {@code ADMIN_VOTACAO} fica sempre o default 1, sem significado.
 */
@Entity
@Table(name = "papeis_votacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PapelVotacao {

    @EmbeddedId private PapelVotacaoId id;

    @Column(name = "peso", nullable = false)
    private BigDecimal peso = BigDecimal.ONE;

    public PapelVotacao(UUID usuarioId, UUID votacaoId, PapelUsuario papel) {
        this.id = new PapelVotacaoId(usuarioId, votacaoId, papel);
    }

    /**
     * Papel com peso explícito. A validação do peso (não negativo, diferente de 1 só com voto
     * ponderado) depende da regra da votação e por isso fica no {@code EleitorService}. {@code
     * null} vale o default 1, em vez de estourar no {@code NOT NULL} da coluna.
     */
    public PapelVotacao(UUID usuarioId, UUID votacaoId, PapelUsuario papel, BigDecimal peso) {
        this(usuarioId, votacaoId, papel);
        this.peso = peso == null ? BigDecimal.ONE : peso;
    }
}
