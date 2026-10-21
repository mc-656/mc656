package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Guarda de publicação de RF-12: mínimo de candidatos em eleição privada, sem banco. */
@ExtendWith(MockitoExtension.class)
class GuardaMinimoCandidatosTest {

    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    @Mock CandidatoOuChapaRepository candidatoRepository;

    @InjectMocks GuardaMinimoCandidatos guarda;

    private static Votacao votacao(TipoVotacao tipo) {
        return new Votacao(
                tipo,
                Instant.parse("2099-10-01T12:00:00Z"),
                Instant.parse("2099-10-02T12:00:00Z"),
                "Eleição",
                "Descrição");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @DisplayName("Bloqueia eleição privada com menos de 2 candidatos/chapas")
    void bloqueiaComMenosDeDois(long quantidade) {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ELEICAO_PRIVADA)));
        when(candidatoRepository.countByVotacaoId(VOTACAO)).thenReturn(quantidade);

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("no mínimo 2 candidatos/chapas");
    }

    @Test
    @DisplayName("Libera eleição privada com 2 candidatos/chapas")
    void liberaComDois() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ELEICAO_PRIVADA)));
        when(candidatoRepository.countByVotacaoId(VOTACAO)).thenReturn(2L);

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Não se aplica a orçamento participativo")
    void ignoraOrcamentoParticipativo() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ORCAMENTO_PARTICIPATIVO)));

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
        verifyNoInteractions(candidatoRepository);
    }

    @Test
    @DisplayName("Votação inexistente gera 404")
    void votacaoInexistente() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
