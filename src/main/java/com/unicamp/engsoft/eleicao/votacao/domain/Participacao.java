package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Registro de <b>que</b> um eleitor votou numa votação (RF-13), nunca de <b>em quê</b> (RNF-02).
 *
 * <p>Append-only (SPECS §7.3): {@link Immutable} faz o Hibernate ignorar alterações, o repositório
 * não expõe update nem delete e um trigger da migração recusa {@code UPDATE}/{@code DELETE} no
 * banco. A {@code UNIQUE (usuario_id, votacao_id)} é a última barreira contra voto duplicado
 * (RF-14) quando duas requisições do mesmo eleitor passam juntas pela checagem do service.
 *
 * <p>Não há vínculo com {@link Cedula}: nenhuma FK, timestamp ou sequência em comum. O id é UUID
 * aleatório (v4, o padrão do {@code GenerationType.UUID}) justamente para não carregar ordem.
 */
@Entity
@Immutable
@Table(name = "participacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    public Participacao(UUID usuarioId, UUID votacaoId) {
        this.usuarioId = usuarioId;
        this.votacaoId = votacaoId;
    }
}
