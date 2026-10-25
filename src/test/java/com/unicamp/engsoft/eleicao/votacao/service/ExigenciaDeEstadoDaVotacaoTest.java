package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * O caminho de checagem de estado, sem banco: leitura com lock (fecha a corrida com uma publicação
 * concorrente) e delegação da regra ao comportamento do estado.
 */
@ExtendWith(MockitoExtension.class)
class ExigenciaDeEstadoDaVotacaoTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    private ExigenciaDeEstadoDaVotacao service;

    @BeforeEach
    void configura() {
        service = new ExigenciaDeEstadoDaVotacao(votacaoRepository);
    }

    private static Votacao votacaoEm(EstadoVotacao estado) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2099-10-01T12:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Eleição do CA",
                        "Descrição");
        votacao.alterarEstado(estado);
        return votacao;
    }

    @Test
    @DisplayName("Usa buscarParaTransicao (lock de escrita) e nunca findById")
    void usaLockDeEscrita() {
        Votacao esperada = votacaoEm(EstadoVotacao.RascunhoVotacao);
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID)).thenReturn(Optional.of(esperada));

        Votacao votacao = service.exigir(VOTACAO_ID, OperacaoVotacao.GERENCIAR_ELEITORES);

        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
        assertThat(votacao).isSameAs(esperada);
    }

    @Test
    @DisplayName("Estado que não permite a operação devolve 409 com a mensagem única")
    void negaOperacaoForaDoEstadoPermitido() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.Publicada)));

        assertThatThrownBy(() -> service.exigir(VOTACAO_ID, OperacaoVotacao.GERENCIAR_CANDIDATOS))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage(
                        "A operação 'gerenciar candidatos/chapas' só pode ser feita com a votação"
                                + " em RascunhoVotacao (estado atual: Publicada)");
    }

    @Test
    @DisplayName("Votação inexistente devolve 404")
    void votacaoInexistente() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigir(VOTACAO_ID, OperacaoVotacao.CONFIGURAR_REGRAS))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
