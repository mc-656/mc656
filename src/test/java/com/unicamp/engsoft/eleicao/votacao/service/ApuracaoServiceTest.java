package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.*;
import com.unicamp.engsoft.eleicao.votacao.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApuracaoServiceTest {
    @Mock VotacaoRepository votacoes;
    @Mock ApuracaoRepository apuracoes;
    @Mock CedulaRepository cedulas;
    @Mock ParticipacaoRepository participacoes;
    @Mock CandidatoOuChapaRepository candidatos;
    @Mock PapelVotacaoRepository papeis;
    @Mock MotorApuracao motor;
    @Mock EstrategiaDesempateDaVotacao desempates;
    @Mock AnulacaoVotacaoService anulacao;
    @Mock Clock clock;
    @InjectMocks ApuracaoService service;
    private final UUID id = UUID.randomUUID();

    private Votacao preparar(TipoVotacao tipo, EstadoVotacao estado) {
        Votacao v =
                new Votacao(
                        tipo,
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(1),
                        "Nome",
                        "Descrição");
        ReflectionTestUtils.setField(v, "id", id);
        v.alterarEstado(estado);
        v.configurarRegras(ModeloVotacao.ELEICAO_CHAPA.regra());
        when(votacoes.buscarParaTransicao(id)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void inexistente() {
        assertThatThrownBy(() -> service.apurar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoApuraAntesDoEncerramento() {
        preparar(TipoVotacao.ELEICAO_PRIVADA, EstadoVotacao.EmVotacao);
        assertThatThrownBy(() -> service.apurar(id)).isInstanceOf(RegraDeNegocioException.class);
        verifyNoInteractions(motor, cedulas);
    }

    @Test
    void chamadaDiretaRecusaOrcamento() {
        preparar(TipoVotacao.ORCAMENTO_PARTICIPATIVO, EstadoVotacao.Apuracao);
        assertThatThrownBy(() -> service.apurar(id)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void semRegras() {
        preparar(TipoVotacao.ELEICAO_PRIVADA, EstadoVotacao.Apuracao).configurarRegras(null);
        assertThatThrownBy(() -> service.apurar(id)).hasMessageContaining("sem regras");
        verifyNoInteractions(motor);
    }

    @Test
    void naoApuraBaseInconsistente() {
        preparar(TipoVotacao.ELEICAO_PRIVADA, EstadoVotacao.Apuracao);
        when(participacoes.countByVotacaoId(id)).thenReturn(1L);
        assertThatThrownBy(() -> service.apurar(id)).hasMessageContaining("Quantidade de cédulas");
        verifyNoInteractions(motor);
    }

    @Test
    void naoContaOpcaoDeOutraVotacao() {
        preparar(TipoVotacao.ELEICAO_PRIVADA, EstadoVotacao.Apuracao);
        when(participacoes.countByVotacaoId(id)).thenReturn(1L);
        when(cedulas.findByVotacaoId(id))
                .thenReturn(List.of(new Cedula(id, UUID.randomUUID(), BigDecimal.ONE)));
        assertThatThrownBy(() -> service.apurar(id)).hasMessageContaining("não pertence");
        verifyNoInteractions(motor);
    }
}
