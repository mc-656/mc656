package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.ConsultaRegraVotacao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adição, listagem e remoção de eleitores com peso (RF-02, RF-10), ponta a ponta com token real e a
 * coluna {@code peso} da V12.
 *
 * <p>A {@link ConsultaRegraVotacao} é substituída por mock porque as regras ainda não são
 * persistidas (#21); o mock devolve {@code Optional.empty()} por padrão, que é o comportamento da
 * implementação real hoje.
 */
@AutoConfigureMockMvc
@Transactional
class EleitorControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/votacoes/{votacaoId}/eleitores";

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    @MockitoBean ConsultaRegraVotacao consultaRegraVotacao;

    private Usuario admin;
    private Usuario bia;
    private Usuario davi;
    private Votacao votacao;

    @BeforeEach
    void semeia() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        bia =
                usuarioRepository.save(
                        new Usuario("Bia", "98765432100", "bia@example.com", "hash-da-senha"));
        davi =
                usuarioRepository.save(
                        new Usuario("Davi", "11144477735", "davi@example.com", "hash-da-senha"));
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

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private static String corpo(String email, String peso) {
        return peso == null
                ? """
                {"email": "%s"}
                """
                        .formatted(email)
                : """
                {"email": "%s", "peso": %s}
                """
                        .formatted(email, peso);
    }

    private void comVotoPonderado() {
        when(consultaRegraVotacao.regraDe(any()))
                .thenReturn(
                        Optional.of(
                                new RegraVotacao(
                                        TipoMaioria.MAIORIA_SIMPLES, null, false, null, true)));
    }

    private void semeiaEleitor(Usuario usuario) {
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(usuario.getId(), votacao.getId(), PapelUsuario.ELEITOR));
    }

    /**
     * Força o estado no banco: publicar pelo {@code VotacaoStateService} exigiria satisfazer todas
     * as guardas de publicação, que não são o foco aqui. O {@code clear} descarta a votação em
     * cache na sessão, que ainda estaria em rascunho.
     */
    private void publicaVotacao() {
        entityManager.flush();
        jdbc.update("UPDATE votacoes SET estado = 'Publicada' WHERE id = ?", votacao.getId());
        entityManager.clear();
    }

    private PapelVotacaoId idEleitor(Usuario usuario) {
        return new PapelVotacaoId(usuario.getId(), votacao.getId(), PapelUsuario.ELEITOR);
    }

    @Test
    @DisplayName("ADMIN_VOTACAO adiciona eleitor sem peso e ele recebe peso 1")
    void adicionaComPesoPadrao() throws Exception {
        // Act
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(bia.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Bia"))
                .andExpect(jsonPath("$.email").value("bia@example.com"))
                .andExpect(jsonPath("$.peso").value(1));

        // Assert
        entityManager.flush();
        BigDecimal peso =
                jdbc.queryForObject(
                        "SELECT peso FROM papeis_votacao WHERE usuario_id = ? AND votacao_id = ?"
                                + " AND papel = 'ELEITOR'",
                        BigDecimal.class,
                        bia.getId(),
                        votacao.getId());
        assertThat(peso).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Peso 1 explícito é aceito em votação sem voto ponderado")
    void aceitaPesoUmExplicito() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", "1.0")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Peso diferente de 1 é aceito em votação com voto ponderado")
    void aceitaPesoComVotoPonderado() throws Exception {
        // Arrange
        comVotoPonderado();

        // Act
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", "2.5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.peso").value(2.5));

        // Assert
        assertThat(papelVotacaoRepository.findById(idEleitor(bia)))
                .get()
                .extracting(PapelVotacao::getPeso)
                .satisfies(p -> assertThat(p).isEqualByComparingTo("2.5"));
    }

    @Test
    @DisplayName("Peso zero é aceito em votação com voto ponderado")
    void aceitaPesoZeroComVotoPonderado() throws Exception {
        // Arrange
        comVotoPonderado();

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", "0")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Peso negativo é rejeitado com 400 e mensagem clara")
    void rejeitaPesoNegativo() throws Exception {
        // Arrange
        comVotoPonderado();

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O peso do eleitor não pode ser negativo"));
        assertThat(papelVotacaoRepository.existsById(idEleitor(bia))).isFalse();
    }

    @Test
    @DisplayName("Peso diferente de 1 sem voto ponderado é rejeitado com 409")
    void rejeitaPesoSemVotoPonderado() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", "2")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Peso diferente de 1 só é permitido em votação com voto"
                                                + " ponderado"));
        assertThat(papelVotacaoRepository.existsById(idEleitor(bia))).isFalse();
    }

    @Test
    @DisplayName("Adicionar o mesmo eleitor duas vezes devolve 409")
    void rejeitaEleitorDuplicado() throws Exception {
        // Arrange
        semeiaEleitor(bia);

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("O administrador também pode ser adicionado como eleitor da própria votação")
    void adminPodeSerEleitor() throws Exception {
        // Act
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("caio@example.com", null)))
                .andExpect(status().isCreated());

        // Assert
        assertThat(papelVotacaoRepository.existsById(idEleitor(admin))).isTrue();
    }

    @Test
    @DisplayName("E-mail sem usuário cadastrado devolve 404")
    void rejeitaEmailDesconhecido() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("ninguem@example.com", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E-mail inválido devolve 400")
    void rejeitaEmailInvalido() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("nao-e-email", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADMIN_VOTACAO lista só os eleitores, em ordem de nome, com o peso")
    void listaEleitores() throws Exception {
        // Arrange
        semeiaEleitor(davi);
        semeiaEleitor(bia);

        // Act + Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Bia"))
                .andExpect(jsonPath("$[0].peso").value(1))
                .andExpect(jsonPath("$[1].nome").value("Davi"));
    }

    @Test
    @DisplayName("ADMIN_VOTACAO remove eleitor e ele some da listagem")
    void removeEleitor() throws Exception {
        // Arrange
        semeiaEleitor(bia);

        // Act
        mvc.perform(
                        delete(URL + "/{usuarioId}", votacao.getId(), bia.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        // Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Remover eleitor não remove o papel ADMIN_VOTACAO do mesmo usuário")
    void removerEleitorPreservaAdmin() throws Exception {
        // Arrange
        semeiaEleitor(admin);

        // Act
        mvc.perform(
                        delete(URL + "/{usuarioId}", votacao.getId(), admin.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        // Assert
        assertThat(papelVotacaoRepository.existsById(idEleitor(admin))).isFalse();
        assertThat(
                        papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO))
                .isTrue();
    }

    @Test
    @DisplayName("Remover usuário que não é eleitor devolve 404")
    void removerNaoEleitor() throws Exception {
        mvc.perform(
                        delete(URL + "/{usuarioId}", votacao.getId(), davi.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Adição fora de RascunhoVotacao é rejeitada com 409")
    void naoAdicionaForaDoRascunho() throws Exception {
        // Arrange
        publicaVotacao();

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("bia@example.com", null)))
                .andExpect(status().isConflict());
        assertThat(papelVotacaoRepository.existsById(idEleitor(bia))).isFalse();
    }

    @Test
    @DisplayName("Remoção fora de RascunhoVotacao é rejeitada com 409")
    void naoRemoveForaDoRascunho() throws Exception {
        // Arrange
        semeiaEleitor(bia);
        publicaVotacao();

        // Act + Assert
        mvc.perform(
                        delete(URL + "/{usuarioId}", votacao.getId(), bia.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        assertThat(papelVotacaoRepository.existsById(idEleitor(bia))).isTrue();
    }

    @Test
    @DisplayName("Listagem continua disponível fora de RascunhoVotacao")
    void listaForaDoRascunho() throws Exception {
        // Arrange
        semeiaEleitor(bia);
        publicaVotacao();

        // Act + Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("ELEITOR recebe 403 ao adicionar eleitor")
    void eleitorNaoAdiciona() throws Exception {
        // Arrange
        semeiaEleitor(bia);

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(bia))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("davi@example.com", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuário sem papel na votação recebe 403 ao listar")
    void semPapelNaoLista() throws Exception {
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(davi)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ELEITOR recebe 403 ao remover eleitor")
    void eleitorNaoRemove() throws Exception {
        // Arrange
        semeiaEleitor(bia);
        semeiaEleitor(davi);

        // Act + Assert
        mvc.perform(
                        delete(URL + "/{usuarioId}", votacao.getId(), davi.getId())
                                .header("Authorization", bearer(bia)))
                .andExpect(status().isForbidden());
        assertThat(papelVotacaoRepository.existsById(idEleitor(davi))).isTrue();
    }

    @Test
    @DisplayName("Requisição sem token recebe 401")
    void semTokenNaoAcessa() throws Exception {
        mvc.perform(get(URL, votacao.getId())).andExpect(status().isUnauthorized());
    }
}
