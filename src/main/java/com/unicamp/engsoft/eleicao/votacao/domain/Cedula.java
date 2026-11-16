package com.unicamp.engsoft.eleicao.votacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * O voto em si: opção escolhida e peso (RF-13), <b>sem nenhuma referência ao eleitor</b> (RNF-02).
 *
 * <p>Para o sigilo valer também para quem lê o banco, a cédula não tem {@code usuario_id}, FK para
 * {@link Participacao}, {@code created_at} nem id sequencial: o id é UUID aleatório (v4). Com um
 * horário ou uma ordem em comum bastaria cruzar com a participação para saber quem votou em quê. O
 * instante de emissão existe só dentro do comprovante criptografado.
 *
 * <p>O {@code peso} é copiado do papel {@code ELEITOR} no momento do voto, para a apuração não
 * depender de um papel que pode mudar depois. Risco residual aceito (SPECS §3, {@code Cedula}): em
 * voto ponderado, um peso único na votação identifica a cédula do seu dono.
 *
 * <p>Append-only, como {@link Participacao}: {@link Immutable}, repositório sem update/delete e
 * trigger no banco.
 */
@Entity
@Immutable
@Table(name = "cedulas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cedula {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    @Column(name = "opcao_id", nullable = false, updatable = false)
    private UUID opcaoId;

    @Column(nullable = false, updatable = false)
    private BigDecimal peso;

    public Cedula(UUID votacaoId, UUID opcaoId, BigDecimal peso) {
        this.votacaoId = votacaoId;
        this.opcaoId = opcaoId;
        this.peso = peso;
    }

    /** Entrada do motor de apuração: só opção e peso, nada que identifique a cédula. */
    public VotoApuravel paraApuracao() {
        return new VotoApuravel(opcaoId, peso);
    }
}
