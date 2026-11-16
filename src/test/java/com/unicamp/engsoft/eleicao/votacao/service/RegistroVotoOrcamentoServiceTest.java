package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.votacao.domain.CedulaOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.Participacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoDuplicadoException;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoForaDaJanelaException;
import com.unicamp.engsoft.eleicao.votacao.dto.AlocacaoPontoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.SaldoOrcamentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotoOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.AlocacaoOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Testes unitários do {@link RegistroVotoOrcamentoService} cobrindo regras de negócio, validação de
 * cotas/saldo e segurança (RF-21, RF-22, RNF-01, RNF-02).
 */
@ExtendWith(MockitoExtension.class)
class RegistroVotoOrcamentoServiceTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PROPOSTA_1 = UUID.randomUUID();
    private static final UUID PROPOSTA_2 = UUID.randomUUID();

    @Mock VotacaoRepository votacaoRepository;
    @Mock ElegibilidadeService elegibilidadeService;
    @Mock PapelVotacaoRepository papelVotacaoRepository;
    @Mock PropostaRepository propostaRepository;
    @Mock ParticipacaoRepository participacaoRepository;
    @Mock CedulaOrcamentoRepository cedulaOrcamentoRepository;
    @Mock AlocacaoOrcamentoRepository alocacaoOrcamentoRepository;
    @Mock ComprovanteService comprovanteService;
    @Mock MetricasVotacao metricasVotacao;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    private RegistroVotoOrcamentoService service;
    private Usuario usuario;
    private Authentication autenticacao;

    @BeforeEach
    void setup() {
        service =
                new RegistroVotoOrcamentoService(
                        votacaoRepository,
                        elegibilidadeService,
                        papelVotacaoRepository,
                        propostaRepository,
                        participacaoRepository,
                        cedulaOrcamentoRepository,
                        alocacaoOrcamentoRepository,
                        comprovanteService,
                        clock,
                        metricasVotacao);

        usuario = new Usuario("Bia", "12345678909", "bia@exemplo.com", "hash");
        usuario.setId(USUARIO_ID);
        autenticacao =
                new UsernamePasswordAuthenticationToken(
                        UsuarioAutenticado.de(usuario), null, List.of());
    }

    private Votacao votacaoAberta() {
        Votacao v =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Orçamento 2026",
                        "Obras locais");
        v.definirOrcamentoTotal(new BigDecimal("100.00"));
        v.alterarEstado(EstadoVotacao.EmVotacao);
        return v;
    }

    private void mockPapel(BigDecimal peso) {
        PapelVotacao papel =
                new PapelVotacao(usuario.getId(), VOTACAO_ID, PapelUsuario.ELEITOR, peso);
        when(papelVotacaoRepository.findById(
                        new PapelVotacaoId(usuario.getId(), VOTACAO_ID, PapelUsuario.ELEITOR)))
                .thenReturn(Optional.of(papel));
    }

    @Test
    @DisplayName("consultarSaldo retorna saldo total e disponível quando eleitor ainda não votou")
    void consultarSaldoAindaNaoVotou() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);

        SaldoOrcamentoResponse response = service.consultarSaldo(VOTACAO_ID, autenticacao);

        assertThat(response.votacaoId()).isEqualTo(VOTACAO_ID);
        assertThat(response.saldoTotal()).isEqualByComparingTo("100.00");
        assertThat(response.saldoDisponivel()).isEqualByComparingTo("100.00");
        assertThat(response.jaVotou()).isFalse();
    }

    @Test
    @DisplayName("consultarSaldo zera o saldo disponível quando eleitor já votou")
    void consultarSaldoJaVotou() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(true);

        SaldoOrcamentoResponse response = service.consultarSaldo(VOTACAO_ID, autenticacao);

        assertThat(response.saldoTotal()).isEqualByComparingTo("100.00");
        assertThat(response.saldoDisponivel()).isEqualByComparingTo("0.00");
        assertThat(response.jaVotou()).isTrue();
    }

    @Test
    @DisplayName("consultarSaldo escala com peso do eleitor quando configurado")
    void consultarSaldoComPeso() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(new BigDecimal("2.5"));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);

        SaldoOrcamentoResponse response = service.consultarSaldo(VOTACAO_ID, autenticacao);

        assertThat(response.saldoTotal()).isEqualByComparingTo("250.00");
        assertThat(response.saldoDisponivel()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("registrar distribui pontos com sucesso dentro do saldo e emite comprovante")
    void registrarSucesso() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);
        mockPapel(BigDecimal.ONE);

        Proposta p1 = new Proposta(VOTACAO_ID, "Reforma Praça", "Desc", new BigDecimal("50.00"));
        Proposta p2 = new Proposta(VOTACAO_ID, "Iluminação", "Desc", new BigDecimal("50.00"));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.of(p1));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_2, VOTACAO_ID))
                .thenReturn(Optional.of(p2));

        CedulaOrcamento cedulaSalva = new CedulaOrcamento(VOTACAO_ID);
        when(cedulaOrcamentoRepository.save(any(CedulaOrcamento.class))).thenReturn(cedulaSalva);
        when(comprovanteService.gerar(eq(VOTACAO_ID), any(), any()))
                .thenReturn("COMPROVANTE-ORCAMENTO-123");

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(
                                new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("40.00")),
                                new AlocacaoPontoRequest(PROPOSTA_2, new BigDecimal("60.00"))));

        ComprovanteResponse response = service.registrar(VOTACAO_ID, request, autenticacao);

        assertThat(response.votacaoId()).isEqualTo(VOTACAO_ID);
        assertThat(response.comprovante()).isEqualTo("COMPROVANTE-ORCAMENTO-123");
        verify(participacaoRepository).saveAndFlush(any(Participacao.class));
        verify(cedulaOrcamentoRepository).save(any(CedulaOrcamento.class));
        verify(alocacaoOrcamentoRepository, org.mockito.Mockito.times(2)).save(any());
        verify(metricasVotacao).votoRegistrado();
    }

    @Test
    @DisplayName("registrar permite votar sem gastar todos os pontos disponíveis")
    void registrarSucessoGastoParcial() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);
        mockPapel(BigDecimal.ONE);

        Proposta p1 = new Proposta(VOTACAO_ID, "Reforma Praça", "Desc", new BigDecimal("50.00"));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.of(p1));

        CedulaOrcamento cedulaSalva = new CedulaOrcamento(VOTACAO_ID);
        when(cedulaOrcamentoRepository.save(any(CedulaOrcamento.class))).thenReturn(cedulaSalva);
        when(comprovanteService.gerar(eq(VOTACAO_ID), any(), any()))
                .thenReturn("COMPROVANTE-ORCAMENTO-123");

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("35.00"))));

        ComprovanteResponse response = service.registrar(VOTACAO_ID, request, autenticacao);

        assertThat(response.comprovante()).isEqualTo("COMPROVANTE-ORCAMENTO-123");
        verify(participacaoRepository).saveAndFlush(any(Participacao.class));
    }

    @Test
    @DisplayName(
            "registrar rejeita quando a soma dos pontos excede o saldo com mensagem indicando o excesso")
    void registrarRejeitaExcessoSaldo() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);
        mockPapel(BigDecimal.ONE);

        Proposta p1 = new Proposta(VOTACAO_ID, "Reforma Praça", "Desc", new BigDecimal("50.00"));
        Proposta p2 = new Proposta(VOTACAO_ID, "Iluminação", "Desc", new BigDecimal("50.00"));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.of(p1));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_2, VOTACAO_ID))
                .thenReturn(Optional.of(p2));

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(
                                new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("60.00")),
                                new AlocacaoPontoRequest(PROPOSTA_2, new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("excede o saldo disponível (100.00) em 10.00 pontos");

        verify(participacaoRepository, never()).saveAndFlush(any());
        verify(cedulaOrcamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar rejeita proposta de outra votação")
    void registrarRejeitaPropostaDeOutraVotacao() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);
        mockPapel(BigDecimal.ONE);

        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.empty());

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("não pertence à votação");
    }

    @Test
    @DisplayName("registrar rejeita pontos negativos")
    void registrarRejeitaPontosNegativos() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);
        mockPapel(BigDecimal.ONE);

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("-5.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("não pode ser negativa");
    }

    @Test
    @DisplayName("registrar bloqueia voto duplicado com 409 Conflict")
    void registrarBloqueiaVotoDuplicado() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(true);

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(VotoDuplicadoException.class);
    }

    @Test
    @DisplayName("registrar bloqueia voto fora da janela EmVotacao")
    void registrarBloqueiaForaDaJanela() {
        Votacao votacao = votacaoAberta();
        votacao.alterarEstado(EstadoVotacao.Suspensa);
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(VotoForaDaJanelaException.class);
    }

    @Test
    @DisplayName("registrar rejeita votação que não é Orçamento Participativo")
    void registrarRejeitaTipoIncompativel() {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Eleição",
                        "Desc");
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("exclusiva para votações de Orçamento Participativo");
    }

    @Test
    @DisplayName("consultarSaldo rejeita votação que não é Orçamento Participativo")
    void consultarSaldoRejeitaTipoIncompativel() {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Eleição",
                        "Desc");
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(votacao));

        assertThatThrownBy(() -> service.consultarSaldo(VOTACAO_ID, autenticacao))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("consultarSaldo com orçamento total nulo assume zero")
    void consultarSaldoOrcamentoTotalNulo() {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Orçamento",
                        "Desc");
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        when(votacaoRepository.findById(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), VOTACAO_ID))
                .thenReturn(false);

        SaldoOrcamentoResponse saldo = service.consultarSaldo(VOTACAO_ID, autenticacao);
        assertThat(saldo.saldoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldo.saldoDisponivel()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("registrar com alocação de zero pontos não cria registro de alocação")
    void registrarIgnoraAlocacaoComZeroPontos() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);
        Proposta p1 = new Proposta(VOTACAO_ID, "P1", "Desc", new BigDecimal("50.00"));
        Proposta p2 = new Proposta(VOTACAO_ID, "P2", "Desc", new BigDecimal("50.00"));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.of(p1));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_2, VOTACAO_ID))
                .thenReturn(Optional.of(p2));
        when(cedulaOrcamentoRepository.save(any(CedulaOrcamento.class)))
                .thenReturn(new CedulaOrcamento(VOTACAO_ID));
        when(comprovanteService.gerar(eq(VOTACAO_ID), any(), any())).thenReturn("COMPROVANTE-123");

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(
                                new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00")),
                                new AlocacaoPontoRequest(PROPOSTA_2, BigDecimal.ZERO)));

        service.registrar(VOTACAO_ID, request, autenticacao);

        verify(alocacaoOrcamentoRepository).save(any());
    }

    @Test
    @DisplayName("validarEAgruparAlocacoes rejeita campos nulos ou request inválido")
    void validarAlocacoesRejeitaNulos() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, null, autenticacao))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(
                        () ->
                                service.registrar(
                                        VOTACAO_ID, new VotoOrcamentoRequest(null), autenticacao))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(
                        () ->
                                service.registrar(
                                        VOTACAO_ID,
                                        new VotoOrcamentoRequest(
                                                List.of(
                                                        new AlocacaoPontoRequest(
                                                                null, BigDecimal.TEN))),
                                        autenticacao))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(
                        () ->
                                service.registrar(
                                        VOTACAO_ID,
                                        new VotoOrcamentoRequest(
                                                List.of(
                                                        new AlocacaoPontoRequest(
                                                                PROPOSTA_1, null))),
                                        autenticacao))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    @DisplayName("violouUnicidadeDoEleitor propaga exceções de integridade genéricas")
    void propagaExcecaoDeIntegridadeGenerica() {
        Votacao votacao = votacaoAberta();
        when(votacaoRepository.buscarParaVoto(VOTACAO_ID)).thenReturn(Optional.of(votacao));
        mockPapel(BigDecimal.ONE);
        Proposta p1 = new Proposta(VOTACAO_ID, "P1", "Desc", new BigDecimal("50.00"));
        when(propostaRepository.findByIdAndVotacaoId(PROPOSTA_1, VOTACAO_ID))
                .thenReturn(Optional.of(p1));
        when(participacaoRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk_erro"));

        VotoOrcamentoRequest request =
                new VotoOrcamentoRequest(
                        List.of(new AlocacaoPontoRequest(PROPOSTA_1, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.registrar(VOTACAO_ID, request, autenticacao))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
