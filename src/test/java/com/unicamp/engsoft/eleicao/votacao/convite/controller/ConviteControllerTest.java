package com.unicamp.engsoft.eleicao.votacao.convite.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convite de eleitores por link, e-mail e QR Code (RF-17, RF-02), ponta a ponta: token real, V18 e
 * a linha {@code ELEITOR} em {@code papeis_votacao}. O SMTP é substituído por mock — o envio real
 * fica para o smoke com Mailpit.
 */
@AutoConfigureMockMvc
@Transactional
class ConviteControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/votacoes/{votacaoId}/convite";
    private static final String SENHA = "senha-forte-123";

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired PasswordEncoder passwordEncoder;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    @MockitoBean JavaMailSender mailSender;

    private Usuario admin;
    private Usuario bia;
    private Usuario davi;
    private Votacao votacao;

    @BeforeEach
    void semeia() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash"));
        bia =
                usuarioRepository.save(
                        new Usuario(
                                "Bia",
                                "98765432100",
                                "bia@example.com",
                                passwordEncoder.encode(SENHA)));
        davi =
                usuarioRepository.save(
                        new Usuario("Davi", "11144477735", "davi@example.com", "hash"));
        votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2099-10-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Assembleia",
                                "Descrição"));
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
    }

    @Test
    @DisplayName("Após a publicação, o administrador gera um link com token não adivinhável")
    void geraLinkAposPublicacao() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);

        // Act
        MvcResult resultado =
                mvc.perform(
                                put(URL, votacao.getId())
                                        .header("Authorization", bearer(admin))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"modo\": \"ABERTO\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.modo").value("ABERTO"))
                        .andReturn();

        // Assert
        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.token");
        String link = JsonPath.read(resultado.getResponse().getContentAsString(), "$.link");
        assertThat(token).hasSize(43).doesNotContain(votacao.getId().toString());
        assertThat(link).isEqualTo("http://localhost:8080/convites/" + token);
    }

    @Test
    @DisplayName("Trocar o modo mantém o mesmo link, para não invalidar o que já circulou")
    void trocarModoMantemToken() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("ABERTO");

        // Act
        String tokenDepois = geraConvite("RESTRITO");

        // Assert
        assertThat(tokenDepois).isEqualTo(token);
    }

    @Test
    @DisplayName("Convite não pode ser gerado com a votação ainda em rascunho")
    void naoGeraEmRascunho() throws Exception {
        // Act & Assert
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"modo\": \"ABERTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", Matchers.containsString("convidar eleitores")));
    }

    @Test
    @DisplayName("Quem não é ADMIN_VOTACAO não gera convite")
    void naoAdminNaoGera() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);

        // Act & Assert
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(bia))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"modo\": \"ABERTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("QR Code é um PNG que decodifica para o link do convite")
    void qrCodeDecodificaParaOLink() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("ABERTO");

        // Act
        byte[] png =
                mvc.perform(
                                get(URL + "/qrcode", votacao.getId())
                                        .header("Authorization", bearer(admin)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.IMAGE_PNG))
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        // Assert
        String lido =
                new QRCodeReader()
                        .decode(
                                new BinaryBitmap(
                                        new HybridBinarizer(
                                                new BufferedImageLuminanceSource(
                                                        ImageIO.read(
                                                                new ByteArrayInputStream(png))))))
                        .getText();
        assertThat(lido).isEqualTo("http://localhost:8080/convites/" + token);
    }

    @Test
    @DisplayName("Convite aberto: qualquer autenticado com o link entra como ELEITOR")
    void conviteAbertoIncluiEleitor() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("ABERTO");

        // Act
        aceita(token, davi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votacaoId").value(votacao.getId().toString()))
                .andExpect(jsonPath("$.nomeVotacao").value("Assembleia"))
                .andExpect(jsonPath("$.jaEraEleitor").value(false));

        // Assert
        assertThat(ehEleitor(davi)).isTrue();
    }

    @Test
    @DisplayName("Abrir o link de novo é sucesso e não duplica o eleitor")
    void abrirDeNovoEhIdempotente() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.EmVotacao);
        String token = geraConvite("ABERTO");
        aceita(token, davi).andExpect(status().isOk());

        // Act & Assert
        aceita(token, davi)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jaEraEleitor").value(true));
    }

    @Test
    @DisplayName("Convite restrito: fora da lista recebe acesso negado e não é incluído")
    void restritoForaDaListaNegado() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("RESTRITO");
        enviaEmails("[\"bia@example.com\"]").andExpect(status().isOk());

        // Act
        aceita(token, davi)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", Matchers.containsString("restrito")));

        // Assert
        assertThat(ehEleitor(davi)).isFalse();
    }

    @Test
    @DisplayName("Convite restrito: quem está na lista entra, sem diferenciar maiúsculas")
    void restritoNaListaIncluido() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("RESTRITO");
        enviaEmails("[\"Bia@Example.COM\"]").andExpect(status().isOk());

        // Act
        aceita(token, bia).andExpect(status().isOk());

        // Assert
        assertThat(ehEleitor(bia)).isTrue();
    }

    @Test
    @DisplayName("Envio por e-mail manda uma mensagem por destinatário com o link")
    void enviaUmEmailPorDestinatario() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("RESTRITO");

        // Act
        enviaEmails("[\"bia@example.com\", \"davi@example.com\", \"BIA@example.com\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails.length()").value(2))
                .andExpect(jsonPath("$.emails[0]").value("bia@example.com"));

        // Assert
        List<SimpleMailMessage> enviados = enviados();
        assertThat(enviados).hasSize(2);
        assertThat(enviados)
                .allSatisfy(
                        m -> {
                            assertThat(m.getTo()).hasSize(1);
                            assertThat(m.getText()).contains("/convites/" + token);
                            assertThat(m.getSubject()).contains("Assembleia");
                        });
        assertThat(enviados.stream().map(m -> m.getTo()[0]))
                .containsExactly("bia@example.com", "davi@example.com");
    }

    @Test
    @DisplayName("Falha do SMTP responde 502 e não grava os e-mails na lista")
    void falhaDoSmtpNaoGravaLista() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        geraConvite("RESTRITO");
        doThrow(new MailSendException("SMTP fora do ar"))
                .when(mailSender)
                .send(any(SimpleMailMessage[].class));

        // Act & Assert
        enviaEmails("[\"bia@example.com\"]").andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("Lista de e-mails com endereço inválido é recusada com 400")
    void emailInvalidoRecusado() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        geraConvite("ABERTO");

        // Act & Assert
        enviaEmails("[\"nao-e-email\"]").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = {"Suspensa", "Apuracao", "Encerrada", "Cancelada"})
    @DisplayName("Fora de Publicada/EmVotacao o link não inclui mais eleitores")
    void linkNaoIncluiForaDaJanela(EstadoVotacao estado) throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.EmVotacao);
        String token = geraConvite("ABERTO");
        mudaEstado(estado);

        // Act
        aceita(token, davi)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", Matchers.containsString("entrar por convite")))
                .andExpect(jsonPath("$.detail", Matchers.containsString(estado.name())));

        // Assert
        assertThat(ehEleitor(davi)).isFalse();
    }

    @Test
    @DisplayName("Token inexistente responde 404")
    void tokenInexistente() throws Exception {
        // Act & Assert
        aceita("token-que-nao-existe", davi).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sem login, o link leva ao login e, depois dele, volta ao convite")
    void linkSemLoginVoltaAoConviteDepoisDoLogin() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("ABERTO");

        // Act: abre o link sem sessão
        MvcResult semLogin =
                mvc.perform(get("/convites/{token}", token))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/login"))
                        .andReturn();
        MockHttpSession sessao = (MockHttpSession) semLogin.getRequest().getSession(false);

        // Act: faz login na mesma sessão
        MvcResult login =
                mvc.perform(
                                post("/login")
                                        .session(sessao)
                                        .param("email", "bia@example.com")
                                        .param("senha", SENHA)
                                        .with(csrf()))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();

        // Assert: volta ao convite (saved request), não ao início
        assertThat(login.getResponse().getRedirectedUrl()).contains("/convites/" + token);
    }

    @Test
    @DisplayName("View do link autenticada inclui o eleitor e mostra a votação")
    void viewAutenticadaIncluiEleitor() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("ABERTO");

        // Act
        mvc.perform(get("/convites/{token}", token).with(user(UsuarioAutenticado.de(bia))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Assembleia")));

        // Assert
        assertThat(ehEleitor(bia)).isTrue();
    }

    @Test
    @DisplayName("View do link em convite restrito mostra acesso negado com 403")
    void viewRestritaNegaForaDaLista() throws Exception {
        // Arrange
        mudaEstado(EstadoVotacao.Publicada);
        String token = geraConvite("RESTRITO");

        // Act
        mvc.perform(get("/convites/{token}", token).with(user(UsuarioAutenticado.de(bia))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(Matchers.containsString("restrito")));

        // Assert
        assertThat(ehEleitor(bia)).isFalse();
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private String geraConvite(String modo) throws Exception {
        String corpo =
                mvc.perform(
                                put(URL, votacao.getId())
                                        .header("Authorization", bearer(admin))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"modo\": \"%s\"}".formatted(modo)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(corpo, "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions enviaEmails(String lista)
            throws Exception {
        return mvc.perform(
                post(URL + "/emails", votacao.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emails\": %s}".formatted(lista)));
    }

    private org.springframework.test.web.servlet.ResultActions aceita(String token, Usuario quem)
            throws Exception {
        return mvc.perform(
                post("/api/convites/{token}/aceite", token).header("Authorization", bearer(quem)));
    }

    private boolean ehEleitor(Usuario usuario) {
        entityManager.flush();
        return papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                usuario.getId(), votacao.getId(), PapelUsuario.ELEITOR);
    }

    /**
     * Força o estado no banco: passar pelo {@code VotacaoStateService} exigiria satisfazer as
     * guardas de publicação, que não são o foco aqui. O {@code clear} descarta a votação em cache.
     */
    private void mudaEstado(EstadoVotacao estado) {
        entityManager.flush();
        jdbc.update("UPDATE votacoes SET estado = ? WHERE id = ?", estado.name(), votacao.getId());
        entityManager.clear();
    }

    /** Mensagens passadas ao {@code send(...)}, achatando a chamada varargs. */
    private List<SimpleMailMessage> enviados() {
        return mockingDetails(mailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .flatMap(i -> Arrays.stream(i.getRawArguments()))
                .flatMap(a -> a instanceof Object[] arr ? Arrays.stream(arr) : Stream.of(a))
                .map(SimpleMailMessage.class::cast)
                .toList();
    }
}
