package com.unicamp.engsoft.eleicao.votacao.controller;

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
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro, listagem e remoção de candidatos/chapas (RF-12), ponta a ponta com token real. */
@AutoConfigureMockMvc
@Transactional
class CandidatoOuChapaControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/votacoes/{votacaoId}/candidatos";

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired CandidatoOuChapaRepository candidatoRepository;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    private Usuario admin;
    private Usuario eleitor;
    private Usuario semPapel;
    private Votacao votacao;

    @BeforeEach
    void semeia() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        eleitor =
                usuarioRepository.save(
                        new Usuario("Bia", "98765432100", "bia@example.com", "hash-da-senha"));
        semPapel =
                usuarioRepository.save(
                        new Usuario("Davi", "11144477735", "davi@example.com", "hash-da-senha"));
        votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2099-10-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Eleição do CA",
                                "Descrição"));
        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(eleitor.getId(), votacao.getId(), PapelUsuario.ELEITOR));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private static String corpo(String nome) {
        return """
                {"nome": "%s", "descricao": "Proposta da chapa"}
                """
                .formatted(nome);
    }

    private CandidatoOuChapa semeiaCandidato(String nome) {
        return candidatoRepository.saveAndFlush(new CandidatoOuChapa(votacao.getId(), nome, null));
    }

    /**
     * Ainda não há implementação de {@code VotacaoStateService} (#24): força o estado no banco. O
     * {@code clear} descarta a votação em cache na sessão, que ainda estaria em rascunho.
     */
    private void publicaVotacao() {
        entityManager.flush();
        jdbc.update("UPDATE votacoes SET estado = 'Publicada' WHERE id = ?", votacao.getId());
        entityManager.clear();
    }

    @Test
    @DisplayName("ADMIN_VOTACAO cadastra candidato em votação em rascunho")
    void adminCadastra() throws Exception {
        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("Chapa Azul")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nome").value("Chapa Azul"))
                .andExpect(jsonPath("$.descricao").value("Proposta da chapa"));
    }

    @Test
    @DisplayName("ADMIN_VOTACAO lista os candidatos na ordem de cadastro")
    void adminLista() throws Exception {
        // Arrange
        semeiaCandidato("Chapa Azul");
        semeiaCandidato("Chapa Verde");

        // Act + Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Chapa Azul"))
                .andExpect(jsonPath("$[1].nome").value("Chapa Verde"));
    }

    @Test
    @DisplayName("ADMIN_VOTACAO remove candidato e ele some da listagem")
    void adminRemove() throws Exception {
        // Arrange
        CandidatoOuChapa candidato = semeiaCandidato("Chapa Azul");

        // Act
        mvc.perform(
                        delete(URL + "/{candidatoId}", votacao.getId(), candidato.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        // Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("ELEITOR recebe 403 ao cadastrar candidato")
    void eleitorNaoCadastra() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("Chapa Azul")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ELEITOR recebe 403 ao remover candidato")
    void eleitorNaoRemove() throws Exception {
        // Arrange
        CandidatoOuChapa candidato = semeiaCandidato("Chapa Azul");

        // Act + Assert
        mvc.perform(
                        delete(URL + "/{candidatoId}", votacao.getId(), candidato.getId())
                                .header("Authorization", bearer(eleitor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ELEITOR consegue listar os candidatos")
    void eleitorLista() throws Exception {
        // Arrange
        semeiaCandidato("Chapa Azul");

        // Act + Assert
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(eleitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Usuário sem papel na votação recebe 403 ao listar")
    void semPapelNaoLista() throws Exception {
        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(semPapel)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cadastro em votação fora de RascunhoVotacao é rejeitado com 409")
    void naoCadastraForaDoRascunho() throws Exception {
        // Arrange
        publicaVotacao();

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("Chapa Azul")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Remoção em votação fora de RascunhoVotacao é rejeitada com 409")
    void naoRemoveForaDoRascunho() throws Exception {
        // Arrange
        CandidatoOuChapa candidato = semeiaCandidato("Chapa Azul");
        publicaVotacao();

        // Act + Assert
        mvc.perform(
                        delete(URL + "/{candidatoId}", votacao.getId(), candidato.getId())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Nome em branco devolve 400")
    void rejeitaNomeEmBranco() throws Exception {
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(" ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Nome repetido na mesma votação devolve 409, ignorando maiúsculas")
    void rejeitaNomeDuplicado() throws Exception {
        // Arrange
        semeiaCandidato("Chapa Azul");

        // Act + Assert
        mvc.perform(
                        post(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("chapa azul")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Remover candidato inexistente devolve 404")
    void removerInexistente() throws Exception {
        mvc.perform(
                        delete(URL + "/{candidatoId}", votacao.getId(), UUID.randomUUID())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
