package com.unicamp.engsoft.eleicao.votacao.controller;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;

@AutoConfigureMockMvc
@Transactional
class VotacaoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired TokenService tokenService;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    private Usuario usuario;

    @BeforeEach
    void semeiaUsuario() {
        usuario =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
    }

    private static String corpoCriacao() {
        return """
                {
                  "tipo": "ELEICAO_PRIVADA",
                  "inicioEm": "2026-10-01T12:00:00Z",
                  "fimEm": "2026-10-02T12:00:00Z",
                  "nome": "Eleição de Teste",
                  "descricao": "Votação criada pelo critério de aceite"
                }
                """;
    }

    @Test
    @DisplayName("ADMIN_VOTACAO cria a votação e ela persiste em RascunhoVotacao")
    void criaVotacaoComEstadoRascunhoEAdmin() throws Exception {
        String token = tokenService.gerar(UsuarioAutenticado.de(usuario));

        String resposta =
                mvc.perform(
                                post("/api/votacoes")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoCriacao()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.estado").value("RascunhoVotacao"))
                        .andExpect(jsonPath("$.nome").value("Eleição de Teste"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        UUID votacaoId = UUID.fromString(JsonPath.read(resposta, "$.id"));

        assertThat(
                        papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                usuario.getId(), votacaoId, PapelUsuario.ADMIN_VOTACAO))
                .isTrue();
        assertThat(votacaoRepository.findById(votacaoId))
                .get()
                .extracting(Votacao::getEstado)
                .isEqualTo(EstadoVotacao.RascunhoVotacao);
    }
}
