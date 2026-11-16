package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votacoes")
public class Votacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoVotacao tipo;

    @Column(name = "inicio_em", nullable = false)
    private Instant inicioEm;

    @Column(name = "fim_em", nullable = false)
    private Instant fimEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoVotacao estado = EstadoVotacao.RascunhoVotacao;

    @Column(name = "nome", nullable = false, length = 50)
    private String nome;

    @Column(name = "descricao", nullable = false, length = 100)
    private String descricao;

    /**
     * Regras de apuração (RF-11), nulas enquanto o rascunho não for configurado. As cinco colunas
     * em {@code votacoes} ficam todas nulas nesse caso.
     */
    @Embedded private RegraVotacao regras;

    /**
     * Orçamento total do orçamento participativo (RF-20), nulo até o organizador defini-lo e sempre
     * nulo em eleição privada. Fica fora de {@link RegraVotacao} por decisão do SPECS §4.1: é
     * definido junto com as propostas, não com as regras de apuração.
     */
    @Column(name = "orcamento_total", precision = 15, scale = 2)
    private BigDecimal orcamentoTotal;

    protected Votacao() {}

    public Votacao(
            TipoVotacao tipo, Instant inicioEm, Instant fimEm, String nome, String descricao) {
        this.tipo = tipo;
        this.inicioEm = inicioEm;
        this.fimEm = fimEm;
        this.descricao = descricao;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public TipoVotacao getTipo() {
        return tipo;
    }

    public Instant getInicioEm() {
        return inicioEm;
    }

    public Instant getFimEm() {
        return fimEm;
    }

    public EstadoVotacao getEstado() {
        return estado;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Regras de apuração, ou {@code null} se ainda não configuradas. */
    public RegraVotacao getRegras() {
        return regras;
    }

    /**
     * Só o {@code VotacaoService} chama este método: a checagem de que a votação está em {@code
     * RascunhoVotacao} fica na camada de service, em {@code ExigenciaDeEstadoDaVotacao}, que
     * pergunta ao comportamento do estado (domínio) se a operação é permitida — junto das outras
     * regras de negócio, nunca aqui na entidade.
     */
    public void configurarRegras(RegraVotacao regras) {
        this.regras = regras;
    }

    /** Orçamento total do orçamento participativo, ou {@code null} se ainda não definido. */
    public BigDecimal getOrcamentoTotal() {
        return orcamentoTotal;
    }

    /**
     * Define o orçamento total (RF-20). Só o {@code PropostaService} chama, depois de checar o
     * estado da votação e que nenhuma proposta custa mais que o novo valor; aqui fica só a
     * invariante que não depende de mais nada: o valor é positivo.
     */
    public void definirOrcamentoTotal(BigDecimal orcamentoTotal) {
        if (orcamentoTotal == null || orcamentoTotal.signum() <= 0) {
            throw new DadosInvalidosException("O orçamento total deve ser maior que zero");
        }
        this.orcamentoTotal = orcamentoTotal;
    }

    /**
     * Só o {@code VotacaoStateService} chama este método: é ele quem valida a transição contra a
     * tabela de transições e registra a auditoria (RNF-03). Público apenas porque o serviço fica em
     * outro pacote.
     */
    public void alterarEstado(EstadoVotacao novoEstado) {
        this.estado = novoEstado;
    }

    /**
     * Prorroga o fim da votação ao retomá-la de {@code Suspensa} (#55). Como {@link
     * #alterarEstado}, só o {@code VotacaoStateService} chama: a prorrogação faz parte da transição
     * auditada, e não uma edição livre da votação.
     */
    public void estenderFim(Instant novoFimEm) {
        if (novoFimEm == null || !novoFimEm.isAfter(fimEm)) {
            throw new DadosInvalidosException(
                    "A nova data de fim deve ser posterior à data de fim atual");
        }
        this.fimEm = novoFimEm;
    }
}
