package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Guarda de publicação de RF-11: regras de apuração precisam existir antes de publicar. */
@ExtendWith(MockitoExtension.class)
class GuardaRegrasConfiguradasTest {

    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    @InjectMocks GuardaRegrasConfiguradas guarda;

    private static Votacao votacao() {
        return new Votacao(
                TipoVotacao.ELEICAO_PRIVADA,
                Instant.parse("2099-10-01T12:00:00Z"),
                Instant.parse("2099-10-02T12:00:00Z"),
                "Eleição",
                "Descrição");
    }

    @Test
    @DisplayName("Bloqueia a publicação enquanto as regras não foram configuradas")
    void bloqueiaSemRegras() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO)).thenReturn(Optional.of(votacao()));

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("regras de apuração");
    }

    @Test
    @DisplayName("Libera a publicação com as regras configuradas")
    void liberaComRegras() {
        // Arrange
        Votacao votacao = votacao();
        votacao.configurarRegras(
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false));
        when(votacaoRepository.findById(VOTACAO)).thenReturn(Optional.of(votacao));

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
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
