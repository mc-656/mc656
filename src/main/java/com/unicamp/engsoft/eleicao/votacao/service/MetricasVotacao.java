package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Métricas de domínio da votação (RNF-06), expostas em {@code /actuator/prometheus} ao lado das
 * métricas HTTP, de JVM e do pool Hikari que o Spring Boot já publica.
 *
 * <ul>
 *   <li>{@code votacao_em_votacao}: gauge de votações em {@code EmVotacao}, consultado no banco a
 *       cada coleta (scrape do Prometheus, não a cada request). Contar no banco evita manter um
 *       contador em memória que divergiria dele após reinício ou com várias instâncias; com o
 *       endpoint restrito ao coletor, é uma consulta por scrape.
 *   <li>{@code votacao_transicoes_total{destino,origem}}: transições efetivadas (já commitadas).
 *   <li>{@code votacao_transicoes_falhas_total{destino,origem,erro}}: transições recusadas, com
 *       erro, ou revertidas no commit ({@code erro="FalhaNoCommit"}). {@code origem="SISTEMA"}
 *       separa falhas dos jobs de abertura/encerramento das tentativas inválidas de um admin.
 *   <li>{@code votacao_votos_registrados_total} e {@code votacao_votos_falhas_total{erro}}: o
 *       registro de voto (#48) ainda não está no {@code develop}; {@link #votoRegistrado()} e
 *       {@link #falhaRegistroVoto(Throwable)} ficam prontos para ele chamar. "Votos por minuto" é
 *       {@code rate(votacao_votos_registrados_total[1m]) * 60} no Prometheus.
 * </ul>
 *
 * <p><b>Sigilo (RNF-02):</b> nenhuma métrica leva usuário, opção votada nem id de votação nos
 * labels. Um contador de votos por opção, ou por votação com poucos eleitores, permitiria deduzir
 * voto observando o incremento logo após um eleitor votar. Os labels também ficam restritos a
 * valores de baixa cardinalidade (enums e nome da exceção).
 *
 * <p>Sucessos só contam depois do commit: uma transição (ou voto) revertida por rollback não
 * aconteceu. Se o commit falhar (flush com violação de constraint, conexão perdida), conta como
 * falha: é justamente a falha de infraestrutura que o monitoramento precisa ver.
 */
@Component
public class MetricasVotacao {

    static final String EM_VOTACAO = "votacao.em_votacao";
    static final String TRANSICOES = "votacao.transicoes";
    static final String TRANSICOES_FALHAS = "votacao.transicoes.falhas";
    static final String VOTOS_REGISTRADOS = "votacao.votos.registrados";
    static final String VOTOS_FALHAS = "votacao.votos.falhas";

    /** Valor do label {@code erro} quando a operação passou na regra mas o commit não ocorreu. */
    static final String FALHA_NO_COMMIT = "FalhaNoCommit";

    private final MeterRegistry registry;
    private final Counter votosRegistrados;

    /**
     * Pré-registrados para aparecerem zerados desde o primeiro scrape: sem a série, {@code
     * rate()}/{@code increase()} não têm ponto de partida e o primeiro evento passa despercebido.
     */
    private final Map<EstadoVotacao, Map<OrigemTransicao, Counter>> contadoresTransicao =
            new EnumMap<>(EstadoVotacao.class);

    public MetricasVotacao(MeterRegistry registry, VotacaoRepository votacaoRepository) {
        this.registry = registry;
        Gauge.builder(
                        EM_VOTACAO,
                        votacaoRepository,
                        repo -> repo.countByEstado(EstadoVotacao.EmVotacao))
                .description("Votações atualmente no estado EmVotacao")
                .register(registry);
        this.votosRegistrados =
                Counter.builder(VOTOS_REGISTRADOS)
                        .description("Votos registrados com sucesso")
                        .register(registry);
        for (EstadoVotacao destino : EstadoVotacao.values()) {
            Map<OrigemTransicao, Counter> porOrigem = new EnumMap<>(OrigemTransicao.class);
            for (OrigemTransicao origem : OrigemTransicao.values()) {
                porOrigem.put(
                        origem,
                        Counter.builder(TRANSICOES)
                                .description("Transições de estado efetivadas")
                                .tag("destino", destino.name())
                                .tag("origem", origem.name())
                                .register(registry));
            }
            contadoresTransicao.put(destino, porOrigem);
        }
    }

    /**
     * Chamado pelo {@link VotacaoStateService} quando a transição passou em todas as regras. Conta
     * como sucesso no commit, ou como falha se a transação for revertida.
     */
    public void transicaoRealizada(EstadoVotacao destino, OrigemTransicao origem) {
        noFimDaTransacao(
                () -> contadoresTransicao.get(destino).get(origem).increment(),
                () -> contarFalhaTransicao(destino, origem, FALHA_NO_COMMIT));
    }

    /** Chamado pelo {@link VotacaoStateService} quando a transição é recusada ou falha. */
    public void falhaTransicao(EstadoVotacao destino, OrigemTransicao origem, Throwable erro) {
        contarFalhaTransicao(destino, origem, erro.getClass().getSimpleName());
    }

    /**
     * Para o serviço de registro de voto (#48) chamar após persistir o voto, dentro da transação.
     * Sem argumentos de propósito: nem eleitor, nem opção, nem votação (RNF-02).
     */
    public void votoRegistrado() {
        noFimDaTransacao(votosRegistrados::increment, () -> contarFalhaVoto(FALHA_NO_COMMIT));
    }

    /** Para o serviço de registro de voto (#48) chamar quando o registro falhar. */
    public void falhaRegistroVoto(Throwable erro) {
        contarFalhaVoto(erro.getClass().getSimpleName());
    }

    private void contarFalhaTransicao(EstadoVotacao destino, OrigemTransicao origem, String erro) {
        Counter.builder(TRANSICOES_FALHAS)
                .description("Transições de estado recusadas, com erro ou revertidas")
                .tag("destino", destino == null ? "desconhecido" : destino.name())
                .tag("origem", origem.name())
                .tag("erro", erro)
                .register(registry)
                .increment();
    }

    private void contarFalhaVoto(String erro) {
        Counter.builder(VOTOS_FALHAS)
                .description("Falhas no registro de voto, por tipo de erro")
                .tag("erro", erro)
                .register(registry)
                .increment();
    }

    /** Sem transação ativa (chamada direta, testes), a operação já está efetivada. */
    private static void noFimDaTransacao(Runnable seCommitou, Runnable seNaoCommitou) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            seCommitou.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            seCommitou.run();
                        } else {
                            seNaoCommitou.run();
                        }
                    }
                });
    }
}
