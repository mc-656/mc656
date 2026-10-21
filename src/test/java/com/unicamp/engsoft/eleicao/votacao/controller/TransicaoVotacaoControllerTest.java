package com.unicamp.engsoft.eleicao.votacao.controller;

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
import java.time.Instant;
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
        Instant inicio = Instant.parse("2099-10-01T12:00:00Z");
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        inicio,
                        inicio.plusSeconds(86400),
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
}
