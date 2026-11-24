package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.SituacaoApuracaoComprovante;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificacaoComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class RegistroVotoServiceTest {

    private final VotacaoRepository votacaoRepository = mock(VotacaoRepository.class);
    private final ElegibilidadeService elegibilidadeService = mock(ElegibilidadeService.class);
    private final PapelVotacaoRepository papelVotacaoRepository =
            mock(PapelVotacaoRepository.class);
    private final CandidatoOuChapaRepository candidatoRepository =
            mock(CandidatoOuChapaRepository.class);
    private final ParticipacaoRepository participacaoRepository =
            mock(ParticipacaoRepository.class);
    private final CedulaRepository cedulaRepository = mock(CedulaRepository.class);
    private final CedulaOrcamentoRepository cedulaOrcamentoRepository =
            mock(CedulaOrcamentoRepository.class);
    private final ComprovanteService comprovanteService = mock(ComprovanteService.class);
    private final ApuracaoRepository apuracaoRepository = mock(ApuracaoRepository.class);
    private final Clock clock = Clock.systemUTC();
    private final MetricasVotacao metricasVotacao = mock(MetricasVotacao.class);

    private final RegistroVotoService service =
            new RegistroVotoService(
                    votacaoRepository,
                    elegibilidadeService,
                    papelVotacaoRepository,
                    candidatoRepository,
                    participacaoRepository,
                    cedulaRepository,
                    cedulaOrcamentoRepository,
                    comprovanteService,
                    apuracaoRepository,
                    clock,
                    metricasVotacao);

    @Test
    void verificacaoSemVotacaoNaoConfirmaRegistro() {
        UUID votacaoId = UUID.randomUUID();
        UUID cedulaId = UUID.randomUUID();
        when(comprovanteService.verificar("codigo"))
                .thenReturn(
                        new ComprovanteService.DadosComprovante(
                                votacaoId, cedulaId, Instant.now()));
        when(cedulaRepository.existsByIdAndVotacaoId(cedulaId, votacaoId)).thenReturn(true);
        when(votacaoRepository.findById(votacaoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificar("codigo"))
                .isInstanceOf(ComprovanteInvalidoException.class)
                .hasMessage("Comprovante não encontrado.");
    }

    @Test
    @DisplayName("usuarioDe extrai UUID de UsuarioAutenticado")
    void usuarioDeUsuarioAutenticado() {
        UUID id = UUID.randomUUID();
        Usuario usuario = new Usuario("Maria", "12345678909", "maria@unicamp.br", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(usuario, "id", id);
        UsuarioAutenticado principal = UsuarioAutenticado.de(usuario);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        assertThat(RegistroVotoService.usuarioDe(auth)).isEqualTo(id);
    }

    @Test
    @DisplayName("usuarioDe extrai UUID do nome da autenticação (ex: JWT sub)")
    void usuarioDeNomeUuid() {
        UUID id = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(id.toString());

        assertThat(RegistroVotoService.usuarioDe(auth)).isEqualTo(id);
    }

    @Test
    @DisplayName("usuarioDe retorna null para autenticação nula, nome nulo ou inválido")
    void usuarioDeCasosNulosEInvalidos() {
        assertThat(RegistroVotoService.usuarioDe(null)).isNull();

        Authentication semNome = mock(Authentication.class);
        when(semNome.getName()).thenReturn(null);
        assertThat(RegistroVotoService.usuarioDe(semNome)).isNull();

        Authentication nomeInvalido = mock(Authentication.class);
        when(nomeInvalido.getName()).thenReturn("nao-e-um-uuid");
        assertThat(RegistroVotoService.usuarioDe(nomeInvalido)).isNull();
    }

    @Test
    @DisplayName("registrar lança UsuarioNaoElegivelException quando usuarioDe é nulo")
    void registrarComUsuarioNuloLancaExcecao() {
        UUID votacaoId = UUID.randomUUID();
        UUID opcaoId = UUID.randomUUID();

        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3600),
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        when(votacaoRepository.buscarParaVoto(votacaoId)).thenReturn(Optional.of(votacao));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("invalido");

        assertThatThrownBy(() -> service.registrar(votacaoId, opcaoId, auth))
                .isInstanceOf(UsuarioNaoElegivelException.class);
    }

    @Test
    @DisplayName("verificar retorna INCLUIDA_NA_APURACAO apenas quando existe snapshot persistido")
    void verificarComESemSnapshot() {
        UUID votacaoId = UUID.randomUUID();
        UUID cedulaId = UUID.randomUUID();
        Instant emitidoEm = Instant.parse("2026-10-06T12:00:00Z");
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        emitidoEm.minusSeconds(60),
                        emitidoEm.plusSeconds(3600),
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(EstadoVotacao.Encerrada);

        when(comprovanteService.verificar("codigo"))
                .thenReturn(
                        new ComprovanteService.DadosComprovante(votacaoId, cedulaId, emitidoEm));
        when(cedulaRepository.existsByIdAndVotacaoId(cedulaId, votacaoId)).thenReturn(true);
        when(votacaoRepository.findById(votacaoId)).thenReturn(Optional.of(votacao));

        when(apuracaoRepository.existsByVotacaoId(votacaoId)).thenReturn(false);
        VerificacaoComprovanteResponse semSnapshot = service.verificar("codigo");
        assertThat(semSnapshot.situacaoApuracao())
                .isEqualTo(SituacaoApuracaoComprovante.NAO_CONFIRMADA);

        when(apuracaoRepository.existsByVotacaoId(votacaoId)).thenReturn(true);
        VerificacaoComprovanteResponse comSnapshot = service.verificar("codigo");
        assertThat(comSnapshot.situacaoApuracao())
                .isEqualTo(SituacaoApuracaoComprovante.INCLUIDA_NA_APURACAO);

        votacao.alterarEstado(EstadoVotacao.Anulada);
        VerificacaoComprovanteResponse anulada = service.verificar("codigo");
        assertThat(anulada.situacaoApuracao())
                .isEqualTo(SituacaoApuracaoComprovante.VOTACAO_ANULADA);
    }
}
