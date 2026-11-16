package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pré-condições de publicação do orçamento participativo (RF-20), sem banco. */
@ExtendWith(MockitoExtension.class)
class GuardaOrcamentoParticipativoTest {

    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    @Mock PropostaRepository propostaRepository;

    @InjectMocks GuardaOrcamentoParticipativo guarda;

    private static Votacao votacao(TipoVotacao tipo) {
        return new Votacao(
                tipo,
                Instant.parse("2099-10-01T12:00:00Z"),
                Instant.parse("2099-10-02T12:00:00Z"),
                "Votação",
                "Descrição");
    }

    private static Votacao orcamentoComTotal() {
        Votacao votacao = votacao(TipoVotacao.ORCAMENTO_PARTICIPATIVO);
        votacao.definirOrcamentoTotal(new BigDecimal("1000.00"));
        return votacao;
    }

    @Test
    @DisplayName("Bloqueia orçamento participativo sem orçamento total definido")
    void bloqueiaSemOrcamentoTotal() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ORCAMENTO_PARTICIPATIVO)));

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("orçamento total ainda não foi definido");
    }

    @Test
    @DisplayName("Bloqueia orçamento participativo sem nenhuma proposta")
    void bloqueiaSemPropostas() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO)).thenReturn(Optional.of(orcamentoComTotal()));
        when(propostaRepository.countByVotacaoId(VOTACAO)).thenReturn(0L);

        // Act + Assert
        assertThatThrownBy(() -> guarda.validar(VOTACAO))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("ao menos uma proposta");
    }

    @Test
    @DisplayName("Libera orçamento participativo com orçamento total e uma proposta")
    void liberaComOrcamentoEProposta() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO)).thenReturn(Optional.of(orcamentoComTotal()));
        when(propostaRepository.countByVotacaoId(VOTACAO)).thenReturn(1L);

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Libera eleição privada sem consultar propostas")
    void liberaEleicaoPrivada() {
        // Arrange
        when(votacaoRepository.findById(VOTACAO))
                .thenReturn(Optional.of(votacao(TipoVotacao.ELEICAO_PRIVADA)));

        // Act + Assert
        assertThatCode(() -> guarda.validar(VOTACAO)).doesNotThrowAnyException();
        verifyNoInteractions(propostaRepository);
    }
}
