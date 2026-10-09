package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

/** Guarda que impede publicar orçamento participativo antes do M3, sem banco. */
@ExtendWith(MockitoExtension.class)
class GuardaOrcamentoParticipativoTest {

    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    @InjectMocks GuardaOrcamentoParticipativo guarda;

    private static Votacao votacao(TipoVotacao tipo) {
        return new Votacao(
                tipo,
                Instant.parse("2099-10-01T12:00:00Z"),
                Instant.parse("2099-10-02T12:00:00Z"),
                "Votação",
                "Descrição");
    }

    @Test
    @DisplayName("Bloqueia orçamento participativo com mensagem de não suportado")
    void bloqueiaOrcamentoParticipativo() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ORCAMENTO_PARTICIPATIVO)));

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("orçamento participativo ainda não suportado");
    }

    @Test
    @DisplayName("Libera eleição privada")
    void liberaEleicaoPrivada() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ELEICAO_PRIVADA)));

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
    }
}
