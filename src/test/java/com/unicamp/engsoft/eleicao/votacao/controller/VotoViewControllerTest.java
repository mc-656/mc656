package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Participacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.ComprovanteService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes das Telas Web do Eleitor (RF-13, RF-14, RNF-01, RNF-02): cédula de votação, bloqueios de
 * voto duplicado e fora da janela, comprovante criptográfico e verificação.
 */
@AutoConfigureMockMvc
@Transactional
class VotoViewControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired CandidatoOuChapaRepository candidatoRepository;

    @Autowired ParticipacaoRepository participacaoRepository;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    @Autowired VotacaoStateService stateService;

    @Autowired ComprovanteService comprovanteService;

    private Usuario admin;
    private Usuario eleitor;
    private Usuario estranho;
    private Votacao votacao;
    private CandidatoOuChapa chapaA;
    private CandidatoOuChapa chapaB;

    @BeforeEach
    void preparaCenario() {
        admin = usuarioRepository.save(new Usuario("Admin", "12345678909", "admin@ex.com", "hash"));
        eleitor =
                usuarioRepository.save(
                        new Usuario("Eleitor", "98765432100", "eleitor@ex.com", "hash"));
        estranho =
                usuarioRepository.save(
                        new Usuario("Estranho", "11144477735", "estranho@ex.com", "hash"));

        votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2020-01-01T10:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Conselho Diretor",
                        "Eleição para o Conselho");
        votacao.configurarRegras(
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false));
        votacao = votacaoRepository.save(votacao);

        chapaA =
                candidatoRepository.save(
                        new CandidatoOuChapa(
                                votacao.getId(), "Chapa Renovação", "Propostas inovadoras"));
        chapaB =
                candidatoRepository.save(
                        new CandidatoOuChapa(
                                votacao.getId(), "Chapa Tradição", "Experiência comprovada"));

        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelVotacaoRepository.save(
                new PapelVotacao(eleitor.getId(), votacao.getId(), PapelUsuario.ELEITOR));

        stateService.transitar(votacao.getId(), EstadoVotacao.Publicada, admin.getId(), null);
        stateService.transitar(votacao.getId(), EstadoVotacao.EmVotacao, null, null);
    }

    private static RequestPostProcessor logado(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    @Test
    @DisplayName("Acesso à cédula de votação sem login redireciona para a tela de login")
    void acessoSemLoginRedireciona() throws Exception {
        mvc.perform(get("/votacoes/{id}/votar", votacao.getId()))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Usuário que não é eleitor da votação recebe 403 Forbidden")
    void naoEleitorRecebe403() throws Exception {
        mvc.perform(get("/votacoes/{id}/votar", votacao.getId()).with(logado(estranho)))
                .andExpect(status().isForbidden());

        mvc.perform(
                        post("/votacoes/{id}/votar", votacao.getId())
                                .with(logado(estranho))
                                .with(csrf())
                                .param("opcaoId", chapaA.getId().toString()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/votacoes/{id}/comprovante", votacao.getId()).with(logado(estranho)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Eleitor acessa a cédula com opções e token CSRF quando a votação está aberta")
    void eleitorAcessaCedulaAberta() throws Exception {
        mvc.perform(get("/votacoes/{id}/votar", votacao.getId()).with(logado(eleitor)))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/votar"))
                .andExpect(content().string(containsString("Conselho Diretor")))
                .andExpect(content().string(containsString("Chapa Renovação")))
                .andExpect(content().string(containsString("Chapa Tradição")))
                .andExpect(content().string(containsString("name=\"opcaoId\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Quórum mínimo")))
                .andExpect(content().string(containsString("Confirmar Voto")));
    }

    @Test
    @DisplayName("Eleitor que já votou vê aviso e não vê o botão de confirmação")
    void eleitorQueJaVotouVeAviso() throws Exception {
        participacaoRepository.saveAndFlush(new Participacao(eleitor.getId(), votacao.getId()));

        mvc.perform(get("/votacoes/{id}/votar", votacao.getId()).with(logado(eleitor)))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/votar"))
                .andExpect(content().string(containsString("Voto já registrado")))
                .andExpect(content().string(not(containsString("Confirmar Voto"))));
    }

    @Test
    @DisplayName("Votação suspensa exibe aviso de indisponibilidade")
    void votacaoSuspensaExibeAviso() throws Exception {
        stateService.transitar(
                votacao.getId(), EstadoVotacao.Suspensa, admin.getId(), "Manutenção urgente");

        mvc.perform(get("/votacoes/{id}/votar", votacao.getId()).with(logado(eleitor)))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/votar"))
                .andExpect(content().string(containsString("Votação indisponível")))
                .andExpect(content().string(not(containsString("Confirmar Voto"))));
    }

    @Test
    @DisplayName("Submissão sem selecionar opção redireciona com mensagem de erro")
    void submissaoSemOpcaoRedirecionaComErro() throws Exception {
        mvc.perform(
                        post("/votacoes/{id}/votar", votacao.getId())
                                .with(logado(eleitor))
                                .with(csrf()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/votar"))
                .andExpect(flash().attribute("erro", "Selecione uma opção para votar."));
    }

    @Test
    @DisplayName("Voto válido registra participação e cédula, redirecionando para o comprovante")
    void votoValidoRegistraERedireciona() throws Exception {
        mvc.perform(
                        post("/votacoes/{id}/votar", votacao.getId())
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("opcaoId", chapaA.getId().toString()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/comprovante"))
                .andExpect(flash().attributeExists("comprovante"))
                .andExpect(flash().attributeExists("emitidoEm"))
                .andExpect(flash().attribute("sucesso", "Voto registrado com sucesso!"));

        assertThat(
                        participacaoRepository.existsByUsuarioIdAndVotacaoId(
                                eleitor.getId(), votacao.getId()))
                .isTrue();
        entityManager.flush();
        Long totalCedulas =
                jdbc.queryForObject(
                        "SELECT count(*) FROM cedulas WHERE votacao_id = ?",
                        Long.class,
                        votacao.getId());
        assertThat(totalCedulas).isEqualTo(1L);
    }

    @Test
    @DisplayName("Tentativa de voto duplicado redireciona com mensagem de erro do domínio")
    void votoDuplicadoRedirecionaComErro() throws Exception {
        // Primeiro voto
        mvc.perform(
                        post("/votacoes/{id}/votar", votacao.getId())
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("opcaoId", chapaA.getId().toString()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/comprovante"));

        // Segundo voto
        mvc.perform(
                        post("/votacoes/{id}/votar", votacao.getId())
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("opcaoId", chapaB.getId().toString()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/votar"))
                .andExpect(flash().attribute("erro", containsString("já registrou voto")));

        entityManager.flush();
        Long totalCedulas =
                jdbc.queryForObject(
                        "SELECT count(*) FROM cedulas WHERE votacao_id = ?",
                        Long.class,
                        votacao.getId());
        assertThat(totalCedulas).isEqualTo(1L);
    }

    @Test
    @DisplayName("Tela de comprovante exibe o código criptográfico quando disponível")
    void telaComprovanteExibeCodigo() throws Exception {
        mvc.perform(
                        get("/votacoes/{id}/comprovante", votacao.getId())
                                .with(logado(eleitor))
                                .flashAttr("comprovante", "COMPROVANTE-TESTE-123")
                                .flashAttr("emitidoEm", Instant.now()))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/comprovante"))
                .andExpect(content().string(containsString("COMPROVANTE-TESTE-123")))
                .andExpect(content().string(containsString("Copiar Comprovante")));
    }

    @Test
    @DisplayName("Tela de comprovante sem flash attribute exibe aviso informativo")
    void telaComprovanteSemFlashExibeAviso() throws Exception {
        mvc.perform(get("/votacoes/{id}/comprovante", votacao.getId()).with(logado(eleitor)))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/comprovante"))
                .andExpect(
                        content().string(containsString("Nenhum comprovante recente disponível")));
    }

    @Test
    @DisplayName("Formulário de verificação GET renderiza com campos vazios")
    void formularioVerificacaoGet() throws Exception {
        mvc.perform(get("/comprovantes/verificar").with(logado(eleitor)))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(content().string(containsString("Verificação de Comprovante de Voto")))
                .andExpect(content().string(containsString("name=\"comprovante\"")));
    }

    @ParameterizedTest
    @CsvSource({
        "EmVotacao, A inclusão na apuração ainda não foi confirmada.",
        "Apuracao, A inclusão na apuração ainda não foi confirmada.",
        "Encerrada, A inclusão na apuração ainda não foi confirmada.",
        "Homologada, A inclusão na apuração ainda não foi confirmada.",
        "Anulada, a votação foi anulada.",
        "Cancelada, a votação foi cancelada."
    })
    @DisplayName("Código recebido no voto confirma registro e informa situação sem revelar escolha")
    void verificarComprovanteValido(EstadoVotacao estado, String mensagem) throws Exception {
        // Verifica exatamente o código entregue ao eleitor no registro do voto.
        var resposta =
                mvc.perform(
                                post("/votacoes/{id}/votar", votacao.getId())
                                        .with(logado(eleitor))
                                        .with(csrf())
                                        .param("opcaoId", chapaA.getId().toString()))
                        .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/comprovante"))
                        .andReturn();

        entityManager.flush();
        String comprovanteReal = (String) resposta.getFlashMap().get("comprovante");
        assertThat(comprovanteReal).isNotBlank();
        votacao.alterarEstado(estado);
        entityManager.flush();

        var verificacao =
                mvc.perform(
                                post("/comprovantes/verificar")
                                        .with(logado(eleitor))
                                        .with(csrf())
                                        .param("comprovante", comprovanteReal))
                        .andExpect(status().isOk())
                        .andExpect(view().name("votacoes/verificar-comprovante"))
                        .andExpect(
                                content().string(containsString("Comprovante Válido e Autêntico!")))
                        .andExpect(content().string(containsString("Conselho Diretor")))
                        .andExpect(content().string(containsString(mensagem)))
                        .andReturn();
        assertThat(verificacao.getResponse().getContentAsString())
                .doesNotContain(chapaA.getNome(), chapaA.getId().toString(), "HMAC-SHA256");
    }

    @Test
    @DisplayName("Verificação de comprovante adulterado exibe mensagem de erro")
    void verificarComprovanteInvalido() throws Exception {
        mvc.perform(
                        post("/comprovantes/verificar")
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("comprovante", "comprovante.totalmente.invalido"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/verificar-comprovante"))
                .andExpect(content().string(containsString("Comprovante não encontrado.")));
    }

    @Test
    @DisplayName(
            "Verificação aceita o código colado com espaços e quebras de linha entre os blocos")
    void verificarComprovanteComEspacos() throws Exception {
        // Arrange: código real do voto, quebrado em blocos de 4 como a tela do comprovante exibe
        var resposta =
                mvc.perform(
                                post("/votacoes/{id}/votar", votacao.getId())
                                        .with(logado(eleitor))
                                        .with(csrf())
                                        .param("opcaoId", chapaA.getId().toString()))
                        .andReturn();
        entityManager.flush();
        String comprovante = (String) resposta.getFlashMap().get("comprovante");
        String emBlocos = comprovante.replaceAll("(.{4})", "$1 ");
        String colado = emBlocos.substring(0, 20) + "\n" + emBlocos.substring(20);

        // Act + Assert
        mvc.perform(
                        post("/comprovantes/verificar")
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("comprovante", colado))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comprovante Válido e Autêntico!")));
    }

    @Test
    void comprovanteDeCedulaInexistenteExibeMesmaMensagem() throws Exception {
        String codigo = comprovanteService.gerar(votacao.getId(), UUID.randomUUID(), Instant.now());
        mvc.perform(
                        post("/comprovantes/verificar")
                                .with(logado(eleitor))
                                .with(csrf())
                                .param("comprovante", codigo))
                .andExpect(status().isOk())
                .andExpect(model().attribute("comprovanteValido", false))
                .andExpect(content().string(containsString("Comprovante não encontrado.")));
    }
}
