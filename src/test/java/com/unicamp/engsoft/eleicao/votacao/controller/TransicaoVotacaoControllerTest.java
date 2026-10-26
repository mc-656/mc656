package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.unicamp.engsoft.eleicao.votacao.service.EncerramentoAutomaticoVotacoes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class TransicaoVotacaoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired TokenService tokenService;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired EncerramentoAutomaticoVotacoes encerramento;

    private Usuario admin;

    private Usuario outro;

    @BeforeEach
    void semeiaUsuarios() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        outro =
                usuarioRepository.save(
                        new Usuario("Bia", "98765432100", "bia@example.com", "hash-da-senha"));
    }

    /**
     * Semeia a votação direto no estado pedido: é preparação de cenário. Publicar pela API
     * dependeria das guardas reais (ex.: mínimo de candidatos da #22).
     */
    private Votacao votacaoDoAdmin(EstadoVotacao estado) {
        return votacaoDoAdmin(estado, Instant.parse("2099-10-02T12:00:00Z"));
    }

    private Votacao votacaoDoAdmin(EstadoVotacao estado, Instant fimEm) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        fimEm.minusSeconds(86400),
                        fimEm,
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        votacao = votacaoRepository.save(votacao);
        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        return votacao;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private static String corpo(EstadoVotacao destino, String motivo) {
        return """
                {"destino": "%s", "motivo": "%s"}
                """
                .formatted(destino, motivo);
    }

    @Test
    @DisplayName("Admin cancela votação publicada e a transição aparece no histórico")
    void cancelaEConsultaHistorico() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Publicada);
        String url = "/api/votacoes/" + votacao.getId() + "/transicoes";

        // Act
        mvc.perform(
                        post(url)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(EstadoVotacao.Cancelada, "Assembleia adiada")))
                .andExpect(status().isNoContent());

        // Assert
        mvc.perform(get(url).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estadoAnterior").value("Publicada"))
                .andExpect(jsonPath("$[0].estadoNovo").value("Cancelada"))
                .andExpect(jsonPath("$[0].autorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$[0].origem").value("USUARIO"))
                .andExpect(jsonPath("$[0].motivo").value("Assembleia adiada"));
    }

    @Test
    @DisplayName("Transição inválida devolve 409 Conflict em ProblemDetail")
    void transicaoInvalidaDevolve409() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.RascunhoVotacao);

        // Act + Assert
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(EstadoVotacao.EmVotacao, "pular publicação")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("Admin não abre a votação manualmente: Publicada → EmVotacao só por data")
    void adminNaoAbreVotacaoManualmente() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Publicada);

        // Act + Assert
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(EstadoVotacao.EmVotacao, "abrir já")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Usuário sem ADMIN_VOTACAO recebe 403 ao transitar ou consultar histórico")
    void naoAdminRecebe403() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Publicada);
        String url = "/api/votacoes/" + votacao.getId() + "/transicoes";

        // Act + Assert
        mvc.perform(
                        post(url)
                                .header("Authorization", bearer(outro))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(EstadoVotacao.Cancelada, "não sou admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", bearer(outro)))
                .andExpect(status().isForbidden());
    }

    private void transitar(Votacao votacao, String json, int statusEsperado) throws Exception {
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().is(statusEsperado));
    }

    private EstadoVotacao estadoDe(Votacao votacao) {
        return votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado();
    }

    @Test
    @DisplayName("Admin suspende e retoma a votação, e as duas transições ficam auditadas")
    void suspendeERetoma() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);
        String url = "/api/votacoes/" + votacao.getId() + "/transicoes";

        // Act
        transitar(votacao, corpo(EstadoVotacao.Suspensa, "Falha na urna"), 204);
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.Suspensa);
        transitar(votacao, "{\"destino\": \"EmVotacao\"}", 204);

        // Assert
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.EmVotacao);
        mvc.perform(get(url).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estadoAnterior").value("EmVotacao"))
                .andExpect(jsonPath("$[0].estadoNovo").value("Suspensa"))
                .andExpect(jsonPath("$[0].origem").value("USUARIO"))
                .andExpect(jsonPath("$[0].motivo").value("Falha na urna"))
                .andExpect(jsonPath("$[1].estadoAnterior").value("Suspensa"))
                .andExpect(jsonPath("$[1].estadoNovo").value("EmVotacao"))
                .andExpect(jsonPath("$[1].autorId").value(admin.getId().toString()));
    }

    @Test
    @DisplayName("Cancelamento forçado funciona a partir de EmVotacao e de Suspensa")
    void cancelamentoForcado() throws Exception {
        // Arrange
        Votacao emVotacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);
        Votacao suspensa = votacaoDoAdmin(EstadoVotacao.Suspensa);

        // Act
        transitar(emVotacao, corpo(EstadoVotacao.Cancelada, "Fraude detectada"), 204);
        transitar(suspensa, corpo(EstadoVotacao.Cancelada, "Falha irrecuperável"), 204);

        // Assert
        assertThat(estadoDe(emVotacao)).isEqualTo(EstadoVotacao.Cancelada);
        assertThat(estadoDe(suspensa)).isEqualTo(EstadoVotacao.Cancelada);
    }

    @Test
    @DisplayName("Suspensão e cancelamento forçado sem motivo devolvem 400 e não alteram o estado")
    void semMotivoDevolve400() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);

        // Act + Assert
        transitar(votacao, "{\"destino\": \"Suspensa\"}", 400);
        transitar(votacao, corpo(EstadoVotacao.Cancelada, "  "), 400);
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName("Transições inválidas da fase de votação devolvem 409")
    void transicoesInvalidasDaFaseDeVotacao() throws Exception {
        // Arrange
        Votacao publicada = votacaoDoAdmin(EstadoVotacao.Publicada);
        Votacao suspensaVencida =
                votacaoDoAdmin(EstadoVotacao.Suspensa, Instant.now().minusSeconds(60));
        Votacao emVotacaoVencida =
                votacaoDoAdmin(EstadoVotacao.EmVotacao, Instant.now().minusSeconds(60));

        // Act + Assert
        transitar(publicada, corpo(EstadoVotacao.Suspensa, "pane"), 409);
        transitar(suspensaVencida, corpo(EstadoVotacao.Apuracao, "fim vencido"), 409);
        transitar(emVotacaoVencida, corpo(EstadoVotacao.Apuracao, "apurar já"), 409);
        assertThat(estadoDe(publicada)).isEqualTo(EstadoVotacao.Publicada);
        assertThat(estadoDe(suspensaVencida)).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(estadoDe(emVotacaoVencida)).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName("Cancelada é estado final: nenhuma transição a partir dela é aceita")
    void canceladaEhFinal() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Cancelada);

        // Act + Assert
        for (EstadoVotacao destino : EstadoVotacao.values()) {
            transitar(votacao, corpo(destino, "tentar reabrir"), 409);
        }
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.Cancelada);
    }

    @Test
    @DisplayName("Usuário sem ADMIN_VOTACAO não suspende a votação")
    void naoAdminNaoSuspende() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);

        // Act + Assert
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(outro))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(EstadoVotacao.Suspensa, "não sou admin")))
                .andExpect(status().isForbidden());
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName("Fim vencido durante a suspensão: prorrogar e retomar mantém a votação aberta")
    void fimVencidoNaSuspensaoProrrogaERetoma() throws Exception {
        // Arrange
        Instant fimVencido = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        Instant novoFim = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS);
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Suspensa, fimVencido);
        encerramento.encerrarVotacoesFinalizadas();
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.Suspensa);

        // Act
        transitar(
                votacao,
                """
                {"destino": "EmVotacao", "motivo": "Retomada", "novoFimEm": "%s"}
                """
                        .formatted(novoFim),
                204);
        encerramento.encerrarVotacoesFinalizadas();

        // Assert
        Votacao atual = votacaoRepository.findById(votacao.getId()).orElseThrow();
        assertThat(atual.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
        assertThat(atual.getFimEm()).isEqualTo(novoFim);
        mvc.perform(
                        get("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].motivo")
                                .value(
                                        "Retomada [fim prorrogado de "
                                                + fimVencido
                                                + " para "
                                                + novoFim
                                                + "]"));
    }

    @Test
    @DisplayName("Fim vencido durante a suspensão: retomar sem prorrogar leva à apuração pelo job")
    void fimVencidoNaSuspensaoRetomaSemProrrogar() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Suspensa, Instant.now().minusSeconds(3600));

        // Act
        transitar(votacao, "{\"destino\": \"EmVotacao\"}", 204);
        encerramento.encerrarVotacoesFinalizadas();

        // Assert
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.Apuracao);
        mvc.perform(
                        get("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estadoNovo").value("EmVotacao"))
                .andExpect(jsonPath("$[0].origem").value("USUARIO"))
                .andExpect(jsonPath("$[1].estadoNovo").value("Apuracao"))
                .andExpect(jsonPath("$[1].origem").value("SISTEMA"))
                .andExpect(jsonPath("$[1].autorId").isEmpty());
    }

    @Test
    @DisplayName("Prorrogação fora da retomada devolve 409")
    void prorrogacaoForaDaRetomadaDevolve409() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);

        // Act + Assert
        transitar(
                votacao,
                """
                {"destino": "Suspensa", "motivo": "pane", "novoFimEm": "2100-01-01T00:00:00Z"}
                """,
                409);
        assertThat(estadoDe(votacao)).isEqualTo(EstadoVotacao.EmVotacao);
    }
}
