package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração das regras de apuração (RF-11) e sua guarda de publicação, ponta a ponta com token
 * real.
 */
@AutoConfigureMockMvc
@Transactional
class RegraVotacaoControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/votacoes/{votacaoId}/regras";

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

    private static String corpoQualificada() {
        return """
                {"tipoMaioria": "MAIORIA_QUALIFICADA", "percentualQualificado": 66.67,
                 "segundoTurno": true, "quorumMinimoPercentual": 40, "votoPonderado": true}
                """;
    }

    /** Segundo turno com maioria simples: combinação proibida pelo value object (RF-11). */
    private static String corpoInvalido() {
        return """
                {"tipoMaioria": "MAIORIA_SIMPLES", "segundoTurno": true}
                """;
    }

    private static String corpoPublicacao() {
        return """
                {"destino": "Publicada", "motivo": "divulgação"}
                """;
    }

    /**
     * Força o estado direto no banco: é preparação de cenário. O {@code clear} descarta a votação
     * em cache na sessão, que ainda estaria em rascunho.
     */
    private void foraDoRascunho() {
        entityManager.flush();
        jdbc.update("UPDATE votacoes SET estado = 'Publicada' WHERE id = ?", votacao.getId());
        entityManager.clear();
    }

    /** Releê a votação do banco para conferir o que de fato foi persistido. */
    private RegraVotacao regrasGravadas() {
        entityManager.flush();
        entityManager.clear();
        return votacaoRepository.findById(votacao.getId()).orElseThrow().getRegras();
    }

    @Test
    @DisplayName("ADMIN_VOTACAO configura regras em rascunho e elas ficam gravadas")
    void adminConfiguraRegras() throws Exception {
        // Act + Assert
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoQualificada()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoMaioria").value("MAIORIA_QUALIFICADA"))
                .andExpect(jsonPath("$.percentualQualificado").value(66.67))
                .andExpect(jsonPath("$.segundoTurno").value(true))
                .andExpect(jsonPath("$.quorumMinimoPercentual").value(40))
                .andExpect(jsonPath("$.votoPonderado").value(true));

        RegraVotacao regras = regrasGravadas();
        assertThat(regras).isNotNull();
        assertThat(regras.tipoMaioria()).isEqualTo(TipoMaioria.MAIORIA_QUALIFICADA);
        assertThat(regras.percentualQualificado()).isEqualByComparingTo("66.67");
        assertThat(regras.segundoTurno()).isTrue();
        assertThat(regras.quorumMinimoPercentual()).isEqualByComparingTo("40");
        assertThat(regras.votoPonderado()).isTrue();
    }

    @Test
    @DisplayName("Combinação inválida de regras devolve 400 com a mensagem do domínio")
    void payloadInvalidoDevolve400() throws Exception {
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoInvalido()))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value("O segundo turno exige maioria absoluta ou qualificada"));

        assertThat(regrasGravadas()).isNull();
    }

    @Test
    @DisplayName("Payload sem tipo de maioria devolve 400")
    void tipoMaioriaAusenteDevolve400() throws Exception {
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Alterar regras fora de RascunhoVotacao é rejeitado com 409")
    void naoConfiguraForaDoRascunho() throws Exception {
        // Arrange
        foraDoRascunho();

        // Act + Assert
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoQualificada()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Usuário sem ADMIN_VOTACAO recebe 403 ao configurar regras")
    void naoAdminRecebe403() throws Exception {
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoQualificada()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(semPapel))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoQualificada()))
                .andExpect(status().isForbidden());

        assertThat(regrasGravadas()).isNull();
    }

    @Test
    @DisplayName("Publicação é bloqueada sem regras e liberada depois de configuradas")
    void publicacaoExigeRegras() throws Exception {
        // Arrange: mínimo de candidatos da guarda da RF-12
        candidatoRepository.saveAndFlush(new CandidatoOuChapa(votacao.getId(), "Chapa Azul", null));
        candidatoRepository.saveAndFlush(
                new CandidatoOuChapa(votacao.getId(), "Chapa Verde", null));

        // Act: publicar sem regras configuradas
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoPublicacao()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("regras de apuração")));

        // Act: configura as regras e tenta de novo
        mvc.perform(
                        put(URL, votacao.getId())
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoQualificada()))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/votacoes/" + votacao.getId() + "/transicoes")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoPublicacao()))
                .andExpect(status().isNoContent());
    }
}
