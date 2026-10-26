package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Opção votável de uma eleição privada (RF-12).
 *
 * <p>Referencia a votação só pelo id, como {@link PapelVotacao}: as operações sobre candidatos
 * nunca precisam navegar pela {@link Votacao}, e a remoção em cascata fica a cargo da FK da V6.
 */
@Entity
@Table(name = "candidatos_chapas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidatoOuChapa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public CandidatoOuChapa(UUID votacaoId, String nome, String descricao) {
        this.votacaoId = votacaoId;
        this.nome = nome;
        this.descricao = descricao;
        this.criadoEm = Instant.now();
    }
}
