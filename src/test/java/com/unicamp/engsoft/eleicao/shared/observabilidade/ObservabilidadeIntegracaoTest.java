package com.unicamp.engsoft.eleicao.shared.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actuator, métricas e id de correlação (RNF-06, #138). {@code @AutoConfigureMetrics} porque o
 * Spring Boot desliga a exportação de métricas em testes por padrão, e sem ela {@code
 * /actuator/prometheus} nem existiria.
 */
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Transactional
class ObservabilidadeIntegracaoTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired TokenService tokenService;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    private Usuario admin;

    /** Conta listada em observabilidade.metricas.emails-autorizados (application-test.yml). */
    private Usuario operador;

    @BeforeEach
    void semeiaUsuarios() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        operador =
                usuarioRepository.save(
                        new Usuario("Ops", "98765432100", "metricas@example.com", "hash-da-senha"));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private Votacao votacaoDoAdminEm(EstadoVotacao estado) {
        Instant fim = Instant.parse("2099-10-02T12:00:00Z");
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        fim.minusSeconds(86400),
                        fim,
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        votacao = votacaoRepository.save(votacao);
        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        return votacao;
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus").header("Authorization", bearer(operador)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"
            })
    @DisplayName("Health e probes são públicos e respondem UP")
    void healthPublico(String caminho) throws Exception {
        // Act & Assert
        mvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** Um token vencido ou forjado não pode derrubar a probe do orquestrador. */
    @Test
    @DisplayName("Health ignora token inválido em vez de responder 401")
    void healthIgnoraTokenInvalido() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/health/readiness").header("Authorization", "Bearer invalido"))
                .andExpect(status().isOk());
    }

    /** Os componentes aparecem (show-components), mas sem detalhes de infraestrutura. */
    @Test
    @DisplayName("Readiness inclui o banco de dados e liveness não")
    void readinessIncluiBanco() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    @DisplayName("Prometheus sem token responde 401, não redireciona para o login")
    void prometheusSemTokenRecusado() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    /** O cadastro é público: só estar logado não pode bastar para ler as métricas. */
    @Test
    @DisplayName("Prometheus com token de conta não autorizada responde 403")
    void prometheusContaNaoAutorizadaRecusada() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/prometheus").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Health aceita HEAD sem credencial, como fazem balanceadores")
    void healthAceitaHead() throws Exception {
        // Act & Assert
        mvc.perform(head("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Prometheus com token expõe métricas HTTP, do pool e o gauge de votações abertas")
    void prometheusComTokenExpoeMetricas() throws Exception {
        // Arrange
        votacaoDoAdminEm(EstadoVotacao.EmVotacao);
        mvc.perform(get("/actuator/health"));

        // Act
        String metricas = scrape();

        // Assert
        assertThat(metricas).contains("http_server_requests_seconds", "hikaricp_connections");
        Matcher gauge = Pattern.compile("(?m)^votacao_em_votacao\\{.*} (\\S+)$").matcher(metricas);
        assertThat(gauge.find()).as("gauge votacao_em_votacao presente").isTrue();
        assertThat(Double.parseDouble(gauge.group(1))).isGreaterThanOrEqualTo(1.0);
        assertThat(metricas).contains("votacao_votos_registrados_total");
    }

    /**
     * A falha é contada na hora; o sucesso só após o commit, que este teste transacional nunca faz
     * (o caminho do sucesso é coberto em {@code VotacaoStateServiceImplTest} e no smoke).
     */
    @Test
    @DisplayName("Transição recusada aparece no contador de falhas por destino")
    void transicaoRecusadaApareceNaMetrica() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdminEm(EstadoVotacao.EmVotacao);
        double antes = falhasParaPublicada(scrape());

        // Act
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"destino\": \"Publicada\"}"))
                .andExpect(status().is4xxClientError());
        double depois = falhasParaPublicada(scrape());

        // Assert
        assertThat(depois - antes).isEqualTo(1.0);
    }

    /** Valor da série de falhas {@code destino=Publicada, origem=USUARIO}, ou 0 se ausente. */
    private static double falhasParaPublicada(String metricas) {
        Matcher m =
                Pattern.compile(
                                "(?m)^votacao_transicoes_falhas_total\\{[^}]*destino=\"Publicada\""
                                        + "[^}]*origem=\"USUARIO\"[^}]*} (\\S+)$")
                        .matcher(metricas);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    @Test
    @DisplayName("Id de correlação recebido é devolvido no header da resposta")
    void ecoaCorrelationId() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER, "req-123"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "req-123"));
    }

    @Test
    @DisplayName("Sem id de correlação, um UUID é gerado, inclusive em resposta 401")
    void geraCorrelationId() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        header().string(
                                        CorrelationIdFilter.HEADER,
                                        matchesPattern("[0-9a-f-]{36}")));
    }

    /** O valor vai para o log: quebra de linha permitiria forjar entradas (log injection). */
    @Test
    @DisplayName("Id de correlação fora do formato seguro é substituído por um novo")
    void substituiCorrelationIdInseguro() throws Exception {
        // Act & Assert
        mvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER, "a\nlinha forjada"))
                .andExpect(
                        header().string(
                                        CorrelationIdFilter.HEADER,
                                        matchesPattern("[0-9a-f-]{36}")));
    }
}
