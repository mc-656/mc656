package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
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
 * {@code configurarRegras} (RF-11) sem banco: a checagem de estado passa pelo ponto único, com lock
 * — era uma das duas cópias que liam com {@code findById} e corriam contra uma publicação.
 */
@ExtendWith(MockitoExtension.class)
class VotacaoServiceTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;

    @Mock PapelVotacaoRepository papelVotacaoRepository;

    private VotacaoService service;

    @BeforeEach
    void configura() {
        service =
                new VotacaoService(
                        votacaoRepository,
                        papelVotacaoRepository,
                        new ExigenciaDeEstadoDaVotacao(votacaoRepository));
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

    private static ConfigurarRegrasRequest regrasValidas() {
        return new ConfigurarRegrasRequest(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
    }

    @Test
    @DisplayName("configurarRegras carrega a votação com lock de escrita, não com findById")
    void configurarRegrasUsaLock() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.RascunhoVotacao)));

        RegraVotacao regra = service.configurarRegras(VOTACAO_ID, regrasValidas());

        assertThat(regra.tipoMaioria()).isEqualTo(TipoMaioria.MAIORIA_SIMPLES);
        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Votação publicada responde 409 com a mensagem única")
    void configurarRegrasForaDoRascunho() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.Publicada)));

        assertThatThrownBy(() -> service.configurarRegras(VOTACAO_ID, regrasValidas()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage(
                        "A operação 'configurar as regras' só pode ser feita com a votação em"
                                + " RascunhoVotacao (estado atual: Publicada)");
    }
}
