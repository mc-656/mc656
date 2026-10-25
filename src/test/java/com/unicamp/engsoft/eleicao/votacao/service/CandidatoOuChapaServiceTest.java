package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
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
 * Cadastro e remoção de candidatos (RF-12) sem banco: a checagem de estado passa pelo ponto único,
 * com lock — era a outra cópia que lia com {@code findById} e corria contra uma publicação.
 */
@ExtendWith(MockitoExtension.class)
class CandidatoOuChapaServiceTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();
    private static final UUID CANDIDATO_ID = UUID.randomUUID();
    private static final String NOME = "Chapa Azul";

    @Mock CandidatoOuChapaRepository candidatoRepository;

    @Mock VotacaoRepository votacaoRepository;

    private CandidatoOuChapaService service;

    @BeforeEach
    void configura() {
        service =
                new CandidatoOuChapaService(
                        candidatoRepository,
                        votacaoRepository,
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

    @Test
    @DisplayName("cadastrar carrega a votação com lock de escrita, não com findById")
    void cadastrarUsaLock() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.RascunhoVotacao)));
        when(candidatoRepository.existsByVotacaoIdAndNomeIgnoreCase(VOTACAO_ID, NOME))
                .thenReturn(false);

        service.cadastrar(VOTACAO_ID, new CadastrarCandidatoRequest(NOME, "Proposta"));

        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
        verify(candidatoRepository).save(any(CandidatoOuChapa.class));
    }

    @Test
    @DisplayName("remover carrega a votação com lock de escrita")
    void removerUsaLock() {
        CandidatoOuChapa candidato = new CandidatoOuChapa(VOTACAO_ID, NOME, null);
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.RascunhoVotacao)));
        when(candidatoRepository.findByIdAndVotacaoId(CANDIDATO_ID, VOTACAO_ID))
                .thenReturn(Optional.of(candidato));

        service.remover(VOTACAO_ID, CANDIDATO_ID);

        verify(votacaoRepository).buscarParaTransicao(VOTACAO_ID);
        verify(votacaoRepository, never()).findById(any());
        verify(candidatoRepository).delete(candidato);
    }

    @Test
    @DisplayName("Votação publicada responde 409 e não cadastra nem remove")
    void operacoesForaDoRascunho() {
        when(votacaoRepository.buscarParaTransicao(VOTACAO_ID))
                .thenReturn(Optional.of(votacaoEm(EstadoVotacao.Publicada)));

        assertThatThrownBy(
                        () ->
                                service.cadastrar(
                                        VOTACAO_ID, new CadastrarCandidatoRequest(NOME, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage(
                        "A operação 'gerenciar candidatos/chapas' só pode ser feita com a votação"
                                + " em RascunhoVotacao (estado atual: Publicada)");

        verify(candidatoRepository, never()).save(any());
        verify(candidatoRepository, never()).delete(any());
    }
}
