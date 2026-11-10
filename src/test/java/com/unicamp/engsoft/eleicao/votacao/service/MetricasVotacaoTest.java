package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Métricas de domínio da votação (RNF-06), sem banco. */
@ExtendWith(MockitoExtension.class)
class MetricasVotacaoTest {

    @Mock VotacaoRepository votacaoRepository;

    private SimpleMeterRegistry registry;

    private MetricasVotacao metricas;

    @BeforeEach
    void configura() {
        registry = new SimpleMeterRegistry();
        metricas = new MetricasVotacao(registry, votacaoRepository);
    }

    @AfterEach
    void limpaSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Gauge reflete a contagem de votações EmVotacao no banco a cada leitura")
    void gaugeConsultaORepositorio() {
        // Arrange
        when(votacaoRepository.countByEstado(EstadoVotacao.EmVotacao)).thenReturn(3L, 5L);

        // Act
        double primeira = registry.get(MetricasVotacao.EM_VOTACAO).gauge().value();
        double segunda = registry.get(MetricasVotacao.EM_VOTACAO).gauge().value();

        // Assert
        assertThat(primeira).isEqualTo(3.0);
        assertThat(segunda).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Voto registrado e falha de registro incrementam seus contadores")
    void contadoresDeVoto() {
        // Act
        metricas.votoRegistrado();
        metricas.votoRegistrado();
        metricas.falhaRegistroVoto(new RegraDeNegocioException("fora da janela"));

        // Assert
        assertThat(registry.get(MetricasVotacao.VOTOS_REGISTRADOS).counter().count())
                .isEqualTo(2.0);
        assertThat(
                        registry.get(MetricasVotacao.VOTOS_FALHAS)
                                .tag("erro", "RegraDeNegocioException")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    /** RNF-02: um label com eleitor, opção ou votação permitiria deduzir o voto pelo incremento. */
    @Test
    @DisplayName("Métricas de voto não carregam labels que identifiquem eleitor, opção ou votação")
    void metricasDeVotoSemDadosSigilosos() {
        // Act
        metricas.votoRegistrado();
        metricas.falhaRegistroVoto(new IllegalStateException());

        // Assert
        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("erro", "destino", "origem"));
        }
    }

    @Test
    @DisplayName("Dentro de transação, a transição só é contada após o commit")
    void transicaoContadaSoAposCommit() {
        // Arrange
        TransactionSynchronizationManager.initSynchronization();

        // Act
        metricas.transicaoRealizada(EstadoVotacao.Suspensa, OrigemTransicao.USUARIO);
        double antes = contadorTransicao("Suspensa", "USUARIO");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        // Assert
        assertThat(antes).isZero();
        assertThat(contadorTransicao("Suspensa", "USUARIO")).isEqualTo(1.0);
    }

    /** Falha no flush/commit é falha de infraestrutura: precisa aparecer no alerta. */
    @Test
    @DisplayName("Transição revertida no commit conta como falha, não como sucesso")
    void transicaoRevertidaContaComoFalha() {
        // Arrange
        TransactionSynchronizationManager.initSynchronization();

        // Act
        metricas.transicaoRealizada(EstadoVotacao.Apuracao, OrigemTransicao.SISTEMA);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // Assert
        assertThat(contadorTransicao("Apuracao", "SISTEMA")).isZero();
        assertThat(
                        registry.get(MetricasVotacao.TRANSICOES_FALHAS)
                                .tags(
                                        "destino",
                                        "Apuracao",
                                        "origem",
                                        "SISTEMA",
                                        "erro",
                                        MetricasVotacao.FALHA_NO_COMMIT)
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Contadores de transição existem zerados antes do primeiro evento")
    void contadoresPreRegistrados() {
        // Act & Assert
        assertThat(contadorTransicao("EmVotacao", "SISTEMA")).isZero();
    }

    private double contadorTransicao(String destino, String origem) {
        return registry.get(MetricasVotacao.TRANSICOES)
                .tags("destino", destino, "origem", origem)
                .counter()
                .count();
    }
}
