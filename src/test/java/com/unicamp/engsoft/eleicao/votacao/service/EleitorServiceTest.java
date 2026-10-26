package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.AdicionarEleitorRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.EleitorResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
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
 * Eleitores (RF-02, RF-10) sem banco: a checagem de estado continua com lock, agora via ponto único
 * compartilhado com os demais services — era a única das três que já usava o lock.
 */
@ExtendWith(MockitoExtension.class)
class EleitorServiceTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String EMAIL = "bia@example.com";

    @Mock PapelVotacaoRepository papelVotacaoRepository;

    @Mock VotacaoRepository votacaoRepository;

    @Mock UsuarioRepository usuarioRepository;

    @Mock ConsultaRegraVotacao consultaRegraVotacao;

    private EleitorService service;

    @BeforeEach
    void configura() {
        service =
                new EleitorService(
                        papelVotacaoRepository,
                        votacaoRepository,
                        usuarioRepository,
                        consultaRegraVotacao,
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

    private static Usuario usuario() {
        return new Usuario("Bia", "98765432100", EMAIL, "hash-da-senha");
    }

    @Test
    @DisplayName("adicionar carrega a votação com lock de escrita, não com findById")
    void adicionarUsaLock() {
        Votacao votacao = votacaoEm(EstadoVotacao.RascunhoVotacao);
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(consultaRegraVotacao.regraDe(votacao)).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(papelVotacaoRepository.existsById(any(PapelVotacaoId.class))).thenReturn(false);
        when(papelVotacaoRepository.save(any(PapelVotacao.class)))
                .thenAnswer(alvo -> alvo.getArgument(0));

        EleitorResponse eleitor =
                service.adicionar(VOTACAO_ID, new AdicionarEleitorRequest(EMAIL, null));

        assertThat(eleitor.peso()).isEqualByComparingTo(BigDecimal.ONE);
        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("remover carrega a votação com lock de escrita")
    void removerUsaLock() {
        PapelVotacao papel = new PapelVotacao(USUARIO_ID, VOTACAO_ID, PapelUsuario.ELEITOR);
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.RascunhoVotacao)));
        when(papelVotacaoRepository.findById(
                        new PapelVotacaoId(USUARIO_ID, VOTACAO_ID, PapelUsuario.ELEITOR)))
                .thenReturn(Optional.of(papel));

        service.remover(VOTACAO_ID, USUARIO_ID);

        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
        verify(papelVotacaoRepository).delete(papel);
    }

    @Test
    @DisplayName("Votação publicada responde 409 com a mensagem única e não altera o colégio")
    void adicionarForaDoRascunho() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.Publicada)));

        assertThatThrownBy(
                        () ->
                                service.adicionar(
                                        VOTACAO_ID, new AdicionarEleitorRequest(EMAIL, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage(
                        "A operação 'gerenciar eleitores' só pode ser feita com a votação em"
                                + " RascunhoVotacao (estado atual: Publicada)");

        verify(usuarioRepository, never()).findByEmail(any());
        verify(papelVotacaoRepository, never()).save(any());
    }
}
