package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.ComprovanteService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voto único com comprovante (RF-13) e bloqueio de duplicado e fora da janela (RF-14), ponta a
 * ponta: HTTP com token real, Postgres real (Testcontainers) e a máquina de estados de verdade
 * levando a votação para {@code EmVotacao}.
 */
@AutoConfigureMockMvc
@Transactional
class VotoControllerTest extends AbstractIntegrationTest {

    private static final String URL_VOTO = "/api/votacoes/{votacaoId}/votos";
    private static final String URL_VERIFICACAO = "/api/comprovantes/verificacao";

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired CandidatoOuChapaRepository candidatoRepository;

    @Autowired VotacaoStateService stateService;

    @Autowired ComprovanteService comprovanteService;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    private Usuario admin;
    private Usuario bia;
    private Usuario davi;
    private Usuario eva;
    private Votacao votacao;
    private CandidatoOuChapa opcaoA;
    private CandidatoOuChapa opcaoB;

    @BeforeEach
    void semeia() {
        admin = usuarioRepository.save(new Usuario("Caio", "12345678909", "caio@ex.com", "hash"));
        bia = usuarioRepository.save(new Usuario("Bia", "98765432100", "bia@ex.com", "hash"));
        davi = usuarioRepository.save(new Usuario("Davi", "11144477735", "davi@ex.com", "hash"));
        eva = usuarioRepository.save(new Usuario("Eva", "52998224725", "eva@ex.com", "hash"));
        votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2020-01-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Assembleia",
                                "Descrição"));
        opcaoA = candidatoRepository.save(new CandidatoOuChapa(votacao.getId(), "Chapa A", null));
        opcaoB = candidatoRepository.save(new CandidatoOuChapa(votacao.getId(), "Chapa B", null));
        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelVotacaoRepository.save(
                new PapelVotacao(
                        bia.getId(), votacao.getId(), PapelUsuario.ELEITOR, new BigDecimal("2.5")));
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(davi.getId(), votacao.getId(), PapelUsuario.ELEITOR));
    }

    // ---------- utilitários ----------

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    /**
     * Publica forçando o estado no banco (as guardas de publicação não são o foco aqui) e abre a
     * votação pela máquina de estados, como o job de abertura faria ({@code autorId} nulo).
     */
    private void abreVotacao() {
        forcaEstado(EstadoVotacao.Publicada);
        stateService.transitar(votacao.getId(), EstadoVotacao.EmVotacao, null, null);
        entityManager.flush();
        entityManager.clear();
    }

    private void forcaEstado(EstadoVotacao estado) {
        entityManager.flush();
        jdbc.update("UPDATE votacoes SET estado = ? WHERE id = ?", estado.name(), votacao.getId());
        entityManager.clear();
    }

    private ResultActions vota(Usuario usuario, UUID votacaoId, UUID opcaoId) throws Exception {
        return mvc.perform(
                post(URL_VOTO, votacaoId)
                        .header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opcaoId\": \"%s\"}".formatted(opcaoId)));
    }

    private ResultActions verifica(Usuario usuario, String comprovante) throws Exception {
        return mvc.perform(
                post(URL_VERIFICACAO)
                        .header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comprovante\": \"%s\"}".formatted(comprovante)));
    }

    private String comprovanteDe(ResultActions resultado) throws Exception {
        return JsonPath.read(
                resultado.andReturn().getResponse().getContentAsString(), "$.comprovante");
    }

    private long contaParticipacoes() {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT count(*) FROM participacoes WHERE votacao_id = ?",
                Long.class,
                votacao.getId());
    }

    private List<Map<String, Object>> cedulas() {
        entityManager.flush();
        return jdbc.queryForList(
                "SELECT opcao_id, peso FROM cedulas WHERE votacao_id = ?", votacao.getId());
    }

    // ---------- RF-13: caminho feliz ----------

    @Test
    @DisplayName("Fluxo completo: eleitor vota, recebe comprovante, verifica e não vota de novo")
    void fluxoCompleto() throws Exception {
        // Arrange
        abreVotacao();

        // Act: vota e recebe o comprovante
        ResultActions voto =
                vota(bia, votacao.getId(), opcaoA.getId())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.votacaoId").value(votacao.getId().toString()))
                        .andExpect(jsonPath("$.comprovante").isNotEmpty())
                        .andExpect(jsonPath("$.emitidoEm").isNotEmpty())
                        .andExpect(jsonPath("$.opcaoId").doesNotExist());
        String comprovante = comprovanteDe(voto);

        // Assert: comprovante verificável por quem votou
        verifica(bia, comprovante)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emitidoEm").isNotEmpty())
                .andExpect(jsonPath("$.votacaoId").value(votacao.getId().toString()))
                .andExpect(jsonPath("$.opcaoId").doesNotExist());

        // Assert: segundo voto rejeitado sem alterar o primeiro
        vota(bia, votacao.getId(), opcaoB.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.startsWith("Voto duplicado")));
        assertThat(contaParticipacoes()).isEqualTo(1);
        assertThat(cedulas())
                .singleElement()
                .satisfies(
                        linha -> {
                            assertThat(linha.get("opcao_id")).isEqualTo(opcaoA.getId());
                            assertThat((BigDecimal) linha.get("peso")).isEqualByComparingTo("2.5");
                        });

        // Assert: votação encerrada recusa voto como "fora da janela"
        forcaEstado(EstadoVotacao.Apuracao);
        vota(davi, votacao.getId(), opcaoA.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.startsWith("Voto fora da janela")));

        // Assert: o admin não tem rota para ler o voto individual
        mvc.perform(get(URL_VOTO, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Eleitor sem peso explícito vota com peso 1")
    void pesoPadraoUm() throws Exception {
        // Arrange
        abreVotacao();

        // Act
        vota(davi, votacao.getId(), opcaoB.getId()).andExpect(status().isCreated());

        // Assert
        assertThat(cedulas())
                .singleElement()
                .satisfies(
                        linha -> {
                            assertThat(linha.get("opcao_id")).isEqualTo(opcaoB.getId());
                            assertThat((BigDecimal) linha.get("peso")).isEqualByComparingTo("1");
                        });
    }

    // ---------- RF-14: duplicado e fora da janela ----------

    @Test
    @DisplayName("Voto antes da abertura (Publicada) é recusado como fora da janela")
    void recusaAntesDaJanela() throws Exception {
        // Arrange
        forcaEstado(EstadoVotacao.Publicada);

        // Act + Assert
        vota(bia, votacao.getId(), opcaoA.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("fora da janela")))
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("Publicada")));
        assertThat(contaParticipacoes()).isZero();
        assertThat(cedulas()).isEmpty();
    }

    @Test
    @DisplayName("Voto durante a suspensão é recusado como fora da janela")
    void recusaDuranteSuspensao() throws Exception {
        // Arrange
        abreVotacao();
        stateService.transitar(
                votacao.getId(), EstadoVotacao.Suspensa, admin.getId(), "Problema técnico");
        entityManager.flush();
        entityManager.clear();

        // Act + Assert
        vota(bia, votacao.getId(), opcaoA.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("Suspensa")));
        assertThat(cedulas()).isEmpty();
    }

    @Test
    @DisplayName("Voto após o encerramento é recusado como fora da janela")
    void recusaAposJanela() throws Exception {
        // Arrange
        forcaEstado(EstadoVotacao.Encerrada);

        // Act + Assert
        vota(bia, votacao.getId(), opcaoA.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.startsWith("Voto fora da janela")));
        assertThat(cedulas()).isEmpty();
    }

    @Test
    @DisplayName("Voto com prazo vencido é recusado mesmo antes do job mudar o estado")
    void recusaComPrazoVencidoAindaEmVotacao() throws Exception {
        // Arrange
        abreVotacao();
        jdbc.update(
                "UPDATE votacoes SET fim_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2021-01-01T00:00:00Z")),
                votacao.getId());
        entityManager.clear();

        // Act + Assert
        vota(bia, votacao.getId(), opcaoA.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.startsWith("Voto fora da janela")));
    }

    // ---------- Elegibilidade e dados ----------

    @Test
    @DisplayName("Usuário que não é eleitor (inclusive o admin) não vota")
    void naoElegivelNaoVota() throws Exception {
        // Arrange
        abreVotacao();

        // Act + Assert
        vota(eva, votacao.getId(), opcaoA.getId()).andExpect(status().isForbidden());
        vota(admin, votacao.getId(), opcaoA.getId()).andExpect(status().isForbidden());
        assertThat(cedulas()).isEmpty();
    }

    @Test
    @DisplayName("Votação inexistente responde 404")
    void votacaoInexistente() throws Exception {
        vota(bia, UUID.randomUUID(), opcaoA.getId()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Opção de outra votação é recusada sem registrar participação")
    void opcaoDeOutraVotacao() throws Exception {
        // Arrange
        abreVotacao();
        Votacao outra =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2020-01-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Outra",
                                "Descrição"));
        CandidatoOuChapa alheia =
                candidatoRepository.save(new CandidatoOuChapa(outra.getId(), "Alheia", null));

        // Act + Assert
        vota(bia, votacao.getId(), alheia.getId()).andExpect(status().isBadRequest());
        assertThat(contaParticipacoes()).isZero();
    }

    @Test
    @DisplayName("Corpo sem opção é recusado com 400")
    void corpoSemOpcao() throws Exception {
        abreVotacao();

        mvc.perform(
                        post(URL_VOTO, votacao.getId())
                                .header("Authorization", bearer(bia))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Voto sem token é recusado com 401")
    void semToken() throws Exception {
        mvc.perform(
                        post(URL_VOTO, votacao.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"opcaoId\": \"%s\"}".formatted(opcaoA.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Comprovante (RNF-01) ----------

    @Test
    @DisplayName("Comprovante adulterado é recusado")
    void comprovanteAdulterado() throws Exception {
        // Arrange
        abreVotacao();
        String comprovante = comprovanteDe(vota(bia, votacao.getId(), opcaoA.getId()));
        char ultimo = comprovante.charAt(comprovante.length() - 1);
        String adulterado =
                comprovante.substring(0, comprovante.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        // Act + Assert
        verifica(bia, adulterado).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Comprovante bem assinado de cédula que não existe é recusado")
    void comprovanteDeCedulaInexistente() throws Exception {
        // Arrange
        String forjado =
                comprovanteService.gerar(votacao.getId(), UUID.randomUUID(), Instant.now());

        // Act + Assert
        verifica(bia, forjado).andExpect(status().isBadRequest());
    }

    // ---------- Sigilo (RNF-02) e append-only ----------

    @Test
    @DisplayName("Admin não tem rota para ler, alterar ou remover voto individual")
    void adminNaoAcessaVotoIndividual() throws Exception {
        // Arrange
        abreVotacao();
        vota(bia, votacao.getId(), opcaoA.getId()).andExpect(status().isCreated());
        entityManager.flush();
        UUID cedulaId =
                jdbc.queryForObject(
                        "SELECT id FROM cedulas WHERE votacao_id = ?", UUID.class, votacao.getId());

        // Act + Assert
        String token = bearer(admin);
        mvc.perform(get(URL_VOTO, votacao.getId()).header("Authorization", token))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        get(URL_VOTO + "/{id}", votacao.getId(), cedulaId)
                                .header("Authorization", token))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        get(URL_VOTO + "/eleitor/{usuarioId}", votacao.getId(), bia.getId())
                                .header("Authorization", token))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        put(URL_VOTO + "/{id}", votacao.getId(), cedulaId)
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"opcaoId\": \"%s\"}".formatted(opcaoB.getId())))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        delete(URL_VOTO + "/{id}", votacao.getId(), cedulaId)
                                .header("Authorization", token))
                .andExpect(status().is4xxClientError());
        assertThat(cedulas())
                .singleElement()
                .extracting(l -> l.get("opcao_id"))
                .isEqualTo(opcaoA.getId());
    }

    @Test
    @DisplayName("Nenhuma coluna de cedulas referencia usuário ou participação")
    void cedulaSemVinculoComUsuario() {
        // Act
        List<String> colunas =
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_name = 'cedulas'",
                        String.class);
        List<String> referenciadas =
                jdbc.queryForList(
                        "SELECT ccu.table_name FROM information_schema.table_constraints tc"
                                + " JOIN information_schema.constraint_column_usage ccu"
                                + " ON tc.constraint_name = ccu.constraint_name"
                                + " WHERE tc.table_name = 'cedulas'"
                                + " AND tc.constraint_type = 'FOREIGN KEY'",
                        String.class);

        // Assert
        assertThat(colunas).containsExactlyInAnyOrder("id", "votacao_id", "opcao_id", "peso");
        assertThat(referenciadas)
                .doesNotContain("usuarios", "participacoes")
                .containsOnly("votacoes", "candidatos_chapas");
    }

    @Test
    @DisplayName("O banco recusa UPDATE em cédula já registrada")
    void bancoRecusaAlterarCedula() throws Exception {
        // Arrange
        abreVotacao();
        vota(bia, votacao.getId(), opcaoA.getId()).andExpect(status().isCreated());
        entityManager.flush();

        // Act + Assert (último comando do teste: o erro aborta a transação no Postgres)
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE cedulas SET opcao_id = ? WHERE votacao_id = ?",
                                        opcaoB.getId(),
                                        votacao.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("O banco recusa DELETE de participação já registrada")
    void bancoRecusaRemoverParticipacao() throws Exception {
        // Arrange
        abreVotacao();
        vota(bia, votacao.getId(), opcaoA.getId()).andExpect(status().isCreated());
        entityManager.flush();

        // Act + Assert (último comando do teste: o erro aborta a transação no Postgres)
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM participacoes WHERE votacao_id = ?",
                                        votacao.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
