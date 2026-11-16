package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Eleição oficial (municipal, geral etc.) exibida no módulo informativo (RF-30).
 *
 * <p>Não tem relação com {@code Votacao}: é dado externo, somente leitura, em tabelas próprias
 * (V15). Por isso não há construtor público nem setters: o conteúdo entra por migração/importação.
 */
@Entity
@Table(name = "eleicoes_publicas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EleicaoPublica {

    @Id private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false)
    private int ano;

    @Column(name = "data_primeiro_turno", nullable = false)
    private LocalDate dataPrimeiroTurno;

    @Column(nullable = false, length = 100)
    private String abrangencia;

    @Embedded private Fonte fonte;
}
