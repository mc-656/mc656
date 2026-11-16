package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificacaoComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.RegistroVotoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/** Testes unitários do {@link VotoViewController} isolando os serviços de domínio. */
@ExtendWith(MockitoExtension.class)
class VotoViewControllerUnitTest {

    @Mock VotacaoService votacaoService;
    @Mock CandidatoOuChapaService candidatoService;
    @Mock RegistroVotoService registroVotoService;
    @Mock ParticipacaoRepository participacaoRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    private VotoViewController controller;
    private MockMvc mvc;
    private Usuario usuario;
    private UsuarioAutenticado usuarioAutenticado;
    private Authentication auth;

    @BeforeEach
    void setup() {
        controller =
                new VotoViewController(
                        votacaoService,
                        candidatoService,
                        registroVotoService,
                        participacaoRepository,
                        clock);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();

        usuario = new Usuario("Maria", "12345678909", "maria@exemplo.com", "hash");
        usuarioAutenticado = UsuarioAutenticado.de(usuario);
        auth = new UsernamePasswordAuthenticationToken(usuarioAutenticado, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Votacao votacaoAberta(UUID id) {
        Votacao v =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Conselho",
                        "Eleição 2026");
        v.alterarEstado(EstadoVotacao.EmVotacao);
        return v;
    }

    @Test
    @DisplayName("GET /votacoes/{id}/votar exibe opções e cédula aberta")
    void exibirCedulaVotacaoAberta() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Votacao votacao = votacaoAberta(votacaoId);
        List<CandidatoOuChapa> candidatos =
                List.of(
                        new CandidatoOuChapa(votacaoId, "Chapa A", "Desc A"),
                        new CandidatoOuChapa(votacaoId, "Chapa B", "Desc B"));

        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), votacaoId))
                .thenReturn(false);
        when(candidatoService.listar(votacaoId)).thenReturn(candidatos);

        mvc.perform(get("/votacoes/{id}/votar", votacaoId).principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/votar"))
                .andExpect(model().attribute("votacao", votacao))
                .andExpect(model().attribute("candidatos", candidatos))
                .andExpect(model().attribute("jaVotou", false))
                .andExpect(model().attribute("aberta", true));
    }

    @Test
    @DisplayName("POST /votacoes/{id}/votar sem opcaoId redireciona com flash de erro")
    void votarSemOpcaoRedirecionaComErro() throws Exception {
        UUID votacaoId = UUID.randomUUID();

        mvc.perform(post("/votacoes/{id}/votar", votacaoId).principal(auth))
                .andExpect(redirectedUrl("/votacoes/" + votacaoId + "/votar"))
                .andExpect(flash().attribute("erro", "Selecione uma opção para votar."));
    }

    @Test
    @DisplayName(
            "POST /votacoes/{id}/votar registra voto e redireciona para comprovante com flash attributes")
    void votarComSucessoRedirecionaParaComprovante() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        UUID opcaoId = UUID.randomUUID();
        Instant emitidoEm = Instant.now();
        ComprovanteResponse resposta =
                new ComprovanteResponse(votacaoId, "HASH-COMPROVANTE-123", emitidoEm);

        when(registroVotoService.registrar(eq(votacaoId), eq(opcaoId), any(Authentication.class)))
                .thenReturn(resposta);

        mvc.perform(
                        post("/votacoes/{id}/votar", votacaoId)
                                .principal(auth)
                                .param("opcaoId", opcaoId.toString()))
                .andExpect(redirectedUrl("/votacoes/" + votacaoId + "/comprovante"))
                .andExpect(flash().attribute("comprovante", "HASH-COMPROVANTE-123"))
                .andExpect(flash().attribute("emitidoEm", emitidoEm))
                .andExpect(flash().attribute("sucesso", "Voto registrado com sucesso!"));

        verify(registroVotoService)
                .registrar(eq(votacaoId), eq(opcaoId), any(Authentication.class));
    }

    @Test
    @DisplayName(
            "POST /votacoes/{id}/votar quando serviço rejeita captura DomainException e redireciona com erro")
    void votarComErroDeDominioRedireciona() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        UUID opcaoId = UUID.randomUUID();

        when(registroVotoService.registrar(eq(votacaoId), eq(opcaoId), any(Authentication.class)))
                .thenThrow(new RegraDeNegocioException("Votação suspensa no momento"));

        mvc.perform(
                        post("/votacoes/{id}/votar", votacaoId)
                                .principal(auth)
                                .param("opcaoId", opcaoId.toString()))
                .andExpect(redirectedUrl("/votacoes/" + votacaoId + "/votar"))
                .andExpect(flash().attribute("erro", "Votação suspensa no momento"));
    }

    @Test
    @DisplayName("GET /votacoes/{id}/comprovante retorna template votacoes/comprovante")
    void telaComprovante() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Votacao votacao = votacaoAberta(votacaoId);
        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);

        mvc.perform(get("/votacoes/{id}/comprovante", votacaoId).principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/comprovante"))
                .andExpect(model().attribute("votacao", votacao));
    }

    @Test
    @DisplayName("GET /comprovantes/verificar sem código retorna template vazio")
    void telaVerificarComprovante() throws Exception {
        mvc.perform(get("/comprovantes/verificar"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"));
    }

    @Test
    @DisplayName("POST /comprovantes/verificar com código válido retorna dados da votação")
    void verificarComprovanteValido() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Instant emitidoEm = Instant.now();
        Votacao votacao = votacaoAberta(votacaoId);
        VerificacaoComprovanteResponse verificado =
                new VerificacaoComprovanteResponse(votacaoId, emitidoEm);

        when(registroVotoService.verificar("COMPROVANTE-OK")).thenReturn(verificado);
        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);

        mvc.perform(post("/comprovantes/verificar").param("comprovante", "COMPROVANTE-OK"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(model().attribute("comprovanteValido", true))
                .andExpect(model().attribute("verificado", verificado))
                .andExpect(model().attribute("votacao", votacao));
    }

    @Test
    @DisplayName("POST /comprovantes/verificar com código em branco exibe erro")
    void verificarComprovanteEmBranco() throws Exception {
        mvc.perform(post("/comprovantes/verificar").param("comprovante", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(model().attribute("erro", "Informe o código do comprovante."));
    }

    @Test
    @DisplayName("GET /comprovantes/verificar com parâmetro codigo delega para verificação")
    void telaVerificarComprovanteComCodigo() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Instant emitidoEm = Instant.now();
        Votacao votacao = votacaoAberta(votacaoId);
        VerificacaoComprovanteResponse verificado =
                new VerificacaoComprovanteResponse(votacaoId, emitidoEm);

        when(registroVotoService.verificar("CODIGO-PARAM")).thenReturn(verificado);
        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);

        mvc.perform(get("/comprovantes/verificar").param("codigo", "CODIGO-PARAM"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(model().attribute("comprovanteValido", true));
    }

    @Test
    @DisplayName("GET /votacoes/{id}/votar quando eleitor já votou exibe aviso")
    void telaVotacaoEleitorJaVotou() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Votacao votacao = votacaoAberta(votacaoId);
        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);
        when(candidatoService.listar(votacaoId)).thenReturn(List.of());
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(any(), eq(votacaoId)))
                .thenReturn(true);

        mvc.perform(get("/votacoes/{id}/votar", votacaoId).principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jaVotou", true));
    }

    @Test
    @DisplayName(
            "GET /votacoes/{id}/votar quando votação não está EmVotacao marca aberta como false")
    void telaVotacaoFechada() throws Exception {
        UUID votacaoId = UUID.randomUUID();
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T23:59:59Z"),
                        "Nome",
                        "Desc");
        votacao.alterarEstado(EstadoVotacao.Publicada);
        when(votacaoService.buscar(votacaoId)).thenReturn(votacao);
        when(candidatoService.listar(votacaoId)).thenReturn(List.of());
        when(participacaoRepository.existsByUsuarioIdAndVotacaoId(any(), eq(votacaoId)))
                .thenReturn(false);

        mvc.perform(get("/votacoes/{id}/votar", votacaoId).principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attribute("aberta", false));
    }

    @Test
    @DisplayName(
            "POST /comprovantes/verificar quando comprovante é inválido adiciona erro ao model")
    void verificarComprovanteInvalidoModel() throws Exception {
        when(registroVotoService.verificar("INVALIDO"))
                .thenThrow(new ComprovanteInvalidoException());

        mvc.perform(post("/comprovantes/verificar").param("comprovante", "INVALIDO"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(model().attribute("comprovanteValido", false))
                .andExpect(model().attributeExists("erro"));
    }

    @Test
    @DisplayName("erroDeDominio trata exceção de domínio e renderiza página de erro com status")
    void erroDeDominioHandler() {
        DomainException ex =
                new DomainException("Erro de domínio de teste", HttpStatus.BAD_REQUEST) {};
        MockHttpServletResponse response = new MockHttpServletResponse();
        Model model = new ConcurrentModel();

        String viewName = controller.erroDeDominio(ex, response, model);

        assertThat(viewName).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.getAttribute("status")).isEqualTo(400);
    }
}
