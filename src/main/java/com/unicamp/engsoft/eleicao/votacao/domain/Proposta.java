package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Item elegível a receber pontos em orçamento participativo (RF-20).
 *
 * <p>Referencia a votação só pelo id, como {@link CandidatoOuChapa}: as operações sobre propostas
 * nunca navegam pela {@link Votacao}, e a remoção em cascata fica a cargo da FK da V14. O custo
 * positivo é invariante da própria proposta, por isso é checado aqui e não só no DTO; a comparação
 * com o orçamento total depende da votação e fica no service.
 */
@Entity
@Table(name = "propostas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proposta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "votacao_id", nullable = false, updatable = false)
    private UUID votacaoId;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal custo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Proposta(UUID votacaoId, String titulo, String descricao, BigDecimal custo) {
        this.votacaoId = votacaoId;
        this.criadoEm = Instant.now();
        alterar(titulo, descricao, custo);
    }

    /**
     * Edita os dados da proposta. Só o {@code PropostaService} chama, depois de checar o estado da
     * votação e o orçamento total.
     */
    public void alterar(String titulo, String descricao, BigDecimal custo) {
        if (custo == null || custo.signum() <= 0) {
            throw new DadosInvalidosException("O custo da proposta deve ser maior que zero");
        }
        this.titulo = titulo;
        this.descricao = descricao;
        this.custo = custo;
    }
}
