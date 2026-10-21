package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.TransicaoVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tabela de transições, guardas e auditoria do state service, sem banco (#24). */
@ExtendWith(MockitoExtension.class)
class VotacaoStateServiceImplTest {

    private static final UUID VOTACAO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-06T12:00:00Z");

    @Mock VotacaoRepository votacaoRepository;

    @Mock TransicaoVotacaoRepository transicaoRepository;

    /** Guardas falsas: registram as chamadas e falham quando configuradas para isso. */
    private final List<UUID> guardasChamadas = new ArrayList<>();

    private boolean guardaFalha;

    private VotacaoStateServiceImpl service;

    @BeforeEach
    void configura() {
        GuardaPublicacao guardaQueRegistra = guardasChamadas::add;
        GuardaPublicacao guardaQuePodeFalhar =
                votacaoId -> {
                    if (guardaFalha) {
                        throw new TransicaoInvalidaException("guarda falsa reprovou");
                    }
                };
        service =
                new VotacaoStateServiceImpl(
                        votacaoRepository,
                        transicaoRepository,
                        List.of(guardaQueRegistra, guardaQuePodeFalhar),
                        Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /** Votação em memória no estado pedido, devolvida pelo repositório mockado. */
    private Votacao votacaoEm(EstadoVotacao estado) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        AGORA.plusSeconds(3600),
                        AGORA.plusSeconds(7200),
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        when(votacaoRepository.buscarParaTransicao(VOTACAO)).thenReturn(Optional.of(votacao));
        return votacao;
    }

    private TransicaoVotacao auditoriaSalva() {
        ArgumentCaptor<TransicaoVotacao> captor = ArgumentCaptor.forClass(TransicaoVotacao.class);
        verify(transicaoRepository).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "{0} -> {1} por {2}")
    @CsvSource({
        "RascunhoVotacao, Publicada, USUARIO",
        "Publicada, RascunhoVotacao, USUARIO",
        "Publicada, EmVotacao, SISTEMA",
        "Publicada, Cancelada, USUARIO"
    })
    @DisplayName("Transições válidas do M1 alteram o estado e geram auditoria")
    void transicoesValidas(EstadoVotacao origem, EstadoVotacao destino, OrigemTransicao quem) {
        // Arrange
        Votacao votacao = votacaoEm(origem);
        UUID autor = quem == OrigemTransicao.USUARIO ? ADMIN : null;

        // Act
        service.transitar(VOTACAO, destino, autor, "motivo do teste");

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(destino);
        TransicaoVotacao auditoria = auditoriaSalva();
        assertThat(auditoria.getVotacaoId()).isEqualTo(VOTACAO);
        assertThat(auditoria.getEstadoAnterior()).isEqualTo(origem);
        assertThat(auditoria.getEstadoNovo()).isEqualTo(destino);
        assertThat(auditoria.getAutorId()).isEqualTo(autor);
        assertThat(auditoria.getOrigem()).isEqualTo(quem);
        assertThat(auditoria.getMotivo()).isEqualTo("motivo do teste");
        assertThat(auditoria.getOcorridaEm()).isEqualTo(AGORA);
    }

    @ParameterizedTest(name = "{0} -> {1} por {2}")
    @CsvSource({
        "RascunhoVotacao, EmVotacao, USUARIO",
        "RascunhoVotacao, Cancelada, USUARIO",
        "Publicada, EmVotacao, USUARIO",
        "RascunhoVotacao, Publicada, SISTEMA",
        "Cancelada, Publicada, USUARIO",
        "EmVotacao, Publicada, USUARIO"
    })
    @DisplayName("Transições inválidas são rejeitadas sem alterar estado nem auditar")
    void transicoesInvalidas(EstadoVotacao origem, EstadoVotacao destino, OrigemTransicao quem) {
        // Arrange
        Votacao votacao = votacaoEm(origem);
        UUID autor = quem == OrigemTransicao.USUARIO ? ADMIN : null;

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, destino, autor, null))
                .isInstanceOf(TransicaoInvalidaException.class)
                .satisfies(
                        e -> {
                            TransicaoInvalidaException ex = (TransicaoInvalidaException) e;
                            assertThat(ex.getEstadoAtual()).isEqualTo(origem);
                            assertThat(ex.getEstadoDestino()).isEqualTo(destino);
                        });
        assertThat(votacao.getEstado()).isEqualTo(origem);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Publicação executa todas as guardas registradas")
    void publicacaoExecutaGuardas() {
        // Arrange
        votacaoEm(EstadoVotacao.RascunhoVotacao);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null);

        // Assert
        assertThat(guardasChamadas).containsExactly(VOTACAO);
    }

    @Test
    @DisplayName("Publicação é bloqueada quando alguma guarda falha")
    void guardaFalhaBloqueiaPublicacao() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.RascunhoVotacao);
        guardaFalha = true;

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("guarda falsa reprovou");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.RascunhoVotacao);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Guardas de publicação não rodam em outras transições")
    void guardasSoNaPublicacao() {
        // Arrange
        votacaoEm(EstadoVotacao.Publicada);
        guardaFalha = true;

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Cancelada, ADMIN, "desistimos");

        // Assert
        assertThat(guardasChamadas).isEmpty();
    }

    @Test
    @DisplayName("Votação inexistente resulta em RecursoNaoEncontradoException")
    void votacaoInexistente() {
        // Arrange
        when(votacaoRepository.buscarParaTransicao(VOTACAO)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
