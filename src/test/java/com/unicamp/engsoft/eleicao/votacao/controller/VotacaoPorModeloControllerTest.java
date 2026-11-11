package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fluxo HTTP de criação por modelo (RF-16): listar modelos, criar, publicar sem mexer nas regras e
 * editar as regras em rascunho.
 */
@AutoConfigureMockMvc
@Transactional
class VotacaoPorModeloControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired TokenService tokenService;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired CandidatoOuChapaRepository candidatoRepository;

    private Usuario usuario;
    private String token;

    @BeforeEach
    void semeiaUsuario() {
        usuario =
                usuarioRepository.save(
                        new Usuario("Lia", "12345678909", "lia@example.com", "hash-da-senha"));
        token = tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private static String corpo(String modelo, String nome, String fim, String opcoesJson) {
        return """
                {
                  "modelo": "%s",
                  "nome": "%s",
                  "inicioEm": "2099-10-01T12:00:00Z",
                  "fimEm": "%s",
                  "opcoes": %s
                }
                """
                .formatted(modelo, nome, fim, opcoesJson);
    }

    private String criar(String corpo) throws Exception {
        return mvc.perform(
                        post("/api/votacoes/por-modelo")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("Lista os três modelos com as regras pré-definidas")
    void listaModelos() throws Exception {
        // Act + Assert
        mvc.perform(get("/api/votacoes/modelos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].codigo").value("ELEICAO_CHAPA"))
                .andExpect(jsonPath("$[0].regras.tipoMaioria").value("MAIORIA_SIMPLES"))
                .andExpect(jsonPath("$[0].votoSecreto").value(true))
                .andExpect(jsonPath("$[1].codigo").value("ASSEMBLEIA"))
                .andExpect(jsonPath("$[1].regras.quorumMinimoPercentual").value(50))
                .andExpect(jsonPath("$[2].codigo").value("DECISAO_SIM_NAO"))
                .andExpect(jsonPath("$[2].opcoesPadrao[0]").value("Sim"))
                .andExpect(jsonPath("$[2].opcoesPadrao[1]").value("Não"));
    }

    @Test
    @DisplayName("Listar modelos exige autenticação")
    void listarModelosSemTokenDevolve401() throws Exception {
        mvc.perform(get("/api/votacoes/modelos")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELEICAO_CHAPA", "ASSEMBLEIA", "DECISAO_SIM_NAO"})
    @DisplayName("Votação criada por modelo é publicada sem nenhuma alteração nas regras")
    void criaEPublicaSemAlterarRegras(String modelo) throws Exception {
        // Arrange
        String opcoes = "DECISAO_SIM_NAO".equals(modelo) ? "null" : "[\"Chapa A\", \"Chapa B\"]";

        // Act
        String resposta = criar(corpo(modelo, "Votação " + modelo, "2099-10-02T12:00:00Z", opcoes));

        // Assert: rascunho, regras do modelo, opções cadastradas e criador como admin
        UUID id = UUID.fromString(JsonPath.read(resposta, "$.votacao.id"));
        assertThat((String) JsonPath.read(resposta, "$.votacao.estado"))
                .isEqualTo("RascunhoVotacao");
        assertThat((String) JsonPath.read(resposta, "$.modelo")).isEqualTo(modelo);
        assertThat((String) JsonPath.read(resposta, "$.regras.tipoMaioria"))
                .isEqualTo("MAIORIA_SIMPLES");
        assertThat((Integer) JsonPath.read(resposta, "$.opcoes.length()")).isEqualTo(2);
        assertThat(votacaoRepository.findById(id).orElseThrow().getRegras()).isNotNull();
        assertThat(candidatoRepository.countByVotacaoId(id)).isEqualTo(2);
        assertThat(
                        papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                usuario.getId(), id, PapelUsuario.ADMIN_VOTACAO))
                .isTrue();

        // Act: publica pelo endpoint de transição existente
        mvc.perform(
                        post("/api/votacoes/" + id + "/transicoes")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"destino\": \"Publicada\"}"))
                .andExpect(status().isNoContent());

        // Assert
        assertThat(votacaoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Publicada);
    }

    @Test
    @DisplayName("Decisão sim/não sem opções informadas gera Sim e Não automaticamente")
    void decisaoSimNaoGeraOpcoes() throws Exception {
        // Act
        String resposta = criar(corpo("DECISAO_SIM_NAO", "Reforma", "2099-10-02T12:00:00Z", "[]"));

        // Assert
        assertThat((String) JsonPath.read(resposta, "$.opcoes[0].nome")).isEqualTo("Sim");
        assertThat((String) JsonPath.read(resposta, "$.opcoes[1].nome")).isEqualTo("Não");
        assertThat((String) JsonPath.read(resposta, "$.votacao.descricao")).isNotBlank();
    }

    @Test
    @DisplayName("Regras preenchidas pelo modelo podem ser alteradas em rascunho")
    void alteraRegrasEmRascunho() throws Exception {
        // Arrange
        String resposta =
                criar(
                        corpo(
                                "ASSEMBLEIA",
                                "Assembleia geral",
                                "2099-10-02T12:00:00Z",
                                "[\"Aprovar\", \"Rejeitar\"]"));
        UUID id = UUID.fromString(JsonPath.read(resposta, "$.votacao.id"));

        // Act
        mvc.perform(
                        put("/api/votacoes/" + id + "/regras")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"tipoMaioria": "MAIORIA_QUALIFICADA",
                                         "percentualQualificado": 66,
                                         "quorumMinimoPercentual": 30}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoMaioria").value("MAIORIA_QUALIFICADA"));

        // Assert
        assertThat(
                        votacaoRepository
                                .findById(id)
                                .orElseThrow()
                                .getRegras()
                                .quorumMinimoPercentual())
                .isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("Fim antes do início é rejeitado e nada é persistido")
    void fimAntesDoInicioRejeitado() throws Exception {
        // Arrange
        long antes = votacaoRepository.count();

        // Act + Assert
        mvc.perform(
                        post("/api/votacoes/por-modelo")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        corpo(
                                                "DECISAO_SIM_NAO",
                                                "Invertida",
                                                "2099-09-30T12:00:00Z",
                                                "null")))
                .andExpect(status().is4xxClientError());
        assertThat(votacaoRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Título vazio é rejeitado com 400")
    void tituloVazioRejeitado() throws Exception {
        mvc.perform(
                        post("/api/votacoes/por-modelo")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        corpo(
                                                "DECISAO_SIM_NAO",
                                                " ",
                                                "2099-10-02T12:00:00Z",
                                                "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Modelo sem opções padrão exige ao menos duas opções")
    void eleicaoChapaSemOpcoesRejeitada() throws Exception {
        // Arrange
        long antes = votacaoRepository.count();

        // Act + Assert
        mvc.perform(
                        post("/api/votacoes/por-modelo")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        corpo(
                                                "ELEICAO_CHAPA",
                                                "Sem chapas",
                                                "2099-10-02T12:00:00Z",
                                                "[\"Única\"]")))
                .andExpect(status().isBadRequest());
        assertThat(votacaoRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Opções repetidas (ignorando caixa) são rejeitadas com 400")
    void opcoesRepetidasRejeitadas() throws Exception {
        mvc.perform(
                        post("/api/votacoes/por-modelo")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        corpo(
                                                "ELEICAO_CHAPA",
                                                "Repetidas",
                                                "2099-10-02T12:00:00Z",
                                                "[\"Chapa A\", \"chapa a\"]")))
                .andExpect(status().isBadRequest());
    }
}
