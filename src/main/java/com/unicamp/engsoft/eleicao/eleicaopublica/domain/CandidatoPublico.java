package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Candidatura registrada numa eleição pública (RF-31). Os dados de candidatura (nome de urna,
 * número, partido) vêm de fonte oficial (TSE), indicada em {@link #fonte}.
 *
 * <p>Não reaproveita {@code CandidatoOuChapa} de votação: aquele é opção votável de uma eleição
 * privada; este é informativo e nunca recebe voto.
 */
@Entity
@Table(name = "candidatos_publicos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidatoPublico {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cargo_id", nullable = false, updatable = false)
    private CargoPublico cargo;

    @Column(name = "nome_urna", nullable = false, length = 100)
    private String nomeUrna;

    @Column(name = "nome_completo", nullable = false, length = 200)
    private String nomeCompleto;

    @Column(nullable = false, length = 10)
    private String numero;

    @Column(name = "partido_sigla", nullable = false, length = 20)
    private String partidoSigla;

    @Column(name = "partido_nome", nullable = false, length = 150)
    private String partidoNome;

    @Embedded private Fonte fonte;
}
