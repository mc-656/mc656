package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ComparecimentoResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComparecimentoServiceTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;
    @Mock ParticipacaoRepository participacaoRepository;
    @Mock PapelVotacaoRepository papelVotacaoRepository;

    @InjectMocks ComparecimentoService service;

    private static Votacao votacao(EstadoVotacao estado, BigDecimal quorum) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-10-01T12:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Eleição",
                        "Descrição");
        votacao.configurarRegras(
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, quorum, false));
        votacao.alterarEstado(estado);
        return votacao;
    }

    @Test
    @DisplayName("Calcula comparecimento e quórum em EmVotacao sem arredondar incorretamente 2/3")
    void arredondamentoExatoDeQuorum() {
        when(votacaoRepository.findById(VOTACAO_ID))
                .thenReturn(Optional.of(votacao(EstadoVotacao.EmVotacao, new BigDecimal("66.67"))));
        when(participacaoRepository.countByVotacaoId(VOTACAO_ID)).thenReturn(2L);
        when(papelVotacaoRepository.countByIdVotacaoIdAndIdPapel(VOTACAO_ID, PapelUsuario.ELEITOR))
                .thenReturn(3L);

        ComparecimentoResponse resp = service.consultar(VOTACAO_ID);

        assertThat(resp.estado()).isEqualTo(EstadoVotacao.EmVotacao);
        assertThat(resp.totalParticipacoes()).isEqualTo(2L);
        assertThat(resp.totalEleitoresAptos()).isEqualTo(3L);
        assertThat(resp.percentualComparecimento()).isEqualByComparingTo("66.67");
        assertThat(resp.quorumMinimoPercentual()).isEqualByComparingTo("66.67");
        assertThat(resp.quorumAtingido()).isFalse();
    }

    @Test
    @DisplayName(
            "Trata 0 eleitores aptos com comparecimento 0% e permite consulta durante Suspensa")
    void zeroAptosESuspensa() {
        when(votacaoRepository.findById(VOTACAO_ID))
                .thenReturn(Optional.of(votacao(EstadoVotacao.Suspensa, new BigDecimal("50"))));
        when(participacaoRepository.countByVotacaoId(VOTACAO_ID)).thenReturn(0L);
        when(papelVotacaoRepository.countByIdVotacaoIdAndIdPapel(VOTACAO_ID, PapelUsuario.ELEITOR))
                .thenReturn(0L);

        ComparecimentoResponse resp = service.consultar(VOTACAO_ID);

        assertThat(resp.estado()).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(resp.percentualComparecimento()).isEqualByComparingTo("0");
        assertThat(resp.quorumAtingido()).isFalse();
    }

    @Test
    @DisplayName("Sem quórum configurado ou com quórum 0%, quorumAtingido é true")
    void semQuorumExigido() {
        Votacao semRegras =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.parse("2026-10-01T12:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "OP",
                        "Desc");
        semRegras.alterarEstado(EstadoVotacao.EmVotacao);

        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(semRegras));
        when(participacaoRepository.countByVotacaoId(VOTACAO_ID)).thenReturn(1L);
        when(papelVotacaoRepository.countByIdVotacaoIdAndIdPapel(VOTACAO_ID, PapelUsuario.ELEITOR))
                .thenReturn(4L);

        ComparecimentoResponse resp = service.consultar(VOTACAO_ID);

        assertThat(resp.percentualComparecimento()).isEqualByComparingTo("25.00");
        assertThat(resp.quorumMinimoPercentual()).isNull();
        assertThat(resp.quorumAtingido()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = {"EmVotacao", "Suspensa"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Recusa consulta de comparecimento em tempo real fora de EmVotacao e Suspensa")
    void recusaForaDaJanela(EstadoVotacao estado) {
        when(votacaoRepository.findById(VOTACAO_ID))
                .thenReturn(Optional.of(votacao(estado, BigDecimal.TEN)));

        assertThatThrownBy(() -> service.consultar(VOTACAO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining(estado.name());
    }

    @Test
    @DisplayName("Votação inexistente lança RecursoNaoEncontradoException")
    void votacaoInexistente() {
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(VOTACAO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
