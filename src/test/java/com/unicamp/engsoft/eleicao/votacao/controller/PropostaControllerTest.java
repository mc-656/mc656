package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
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
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propostas e orçamento total do orçamento participativo (RF-20), ponta a ponta com token real: a
 * votação é criada, configurada e publicada pelos próprios endpoints, sem atalhos no banco.
 */
@AutoConfigureMockMvc
@Transactional
class PropostaControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    private Usuario admin;
    private Usuario eleitor;
    private Usuario semPapel;

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
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private UUID criaVotacao(String tipo) throws Exception {
        String corpo =
                """
                {"tipo": "%s", "inicioEm": "2099-10-01T12:00:00Z",
                 "fimEm": "2099-10-02T12:00:00Z", "nome": "Orçamento do bairro",
                 "descricao": "Obras de 2099"}
                """
                        .formatted(tipo);
        String resposta =
                mvc.perform(
                                post("/api/votacoes")
                                        .header("Authorization", bearer(admin))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID votacaoId = UUID.fromString(JsonPath.read(resposta, "$.id"));
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(eleitor.getId(), votacaoId, PapelUsuario.ELEITOR));
        return votacaoId;
    }

    private UUID criaOrcamentoParticipativo() throws Exception {
        return criaVotacao("ORCAMENTO_PARTICIPATIVO");
    }

    private ResultActions definirOrcamento(UUID votacaoId, String valor) throws Exception {
        return mvc.perform(
                put("/api/votacoes/{id}/orcamento", votacaoId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orcamentoTotal\": %s}".formatted(valor)));
    }

    private static String proposta(String titulo, String custo) {
        return """
                {"titulo": "%s", "descricao": "Descrição de %s", "custo": %s}
                """
                .formatted(titulo, titulo, custo);
    }

    private ResultActions cadastrar(UUID votacaoId, Usuario usuario, String corpo)
            throws Exception {
        return mvc.perform(
                post("/api/votacoes/{id}/propostas", votacaoId)
                        .header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo));
    }

    private UUID cadastrarProposta(UUID votacaoId, String titulo, String custo) throws Exception {
        String resposta =
                cadastrar(votacaoId, admin, proposta(titulo, custo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(JsonPath.read(resposta, "$.proposta.id"));
    }

    private void configuraRegras(UUID votacaoId) throws Exception {
        mvc.perform(
                        put("/api/votacoes/{id}/regras", votacaoId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipoMaioria\": \"MAIORIA_SIMPLES\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions publicar(UUID votacaoId) throws Exception {
        return mvc.perform(
                post("/api/votacoes/{id}/transicoes", votacaoId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destino\": \"Publicada\"}"));
    }

    @Test
    @DisplayName(
            "Fluxo completo: orçamento, propostas, rejeição acima do orçamento, aviso na 11ª,"
                    + " publicação e bloqueio de edição depois de publicada")
    void fluxoCompletoAtePublicacao() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        configuraRegras(votacaoId);

        // Act + Assert: orçamento definido
        definirOrcamento(votacaoId, "1000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamentoTotal").value(1000.00));

        // Act + Assert: proposta válida, sem avisos
        cadastrar(votacaoId, admin, proposta("Praça nova", "400.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposta.titulo").value("Praça nova"))
                .andExpect(jsonPath("$.proposta.custo").value(400.00))
                .andExpect(jsonPath("$.avisos.length()").value(0));

        // Act + Assert: custo acima do orçamento é rejeitado com mensagem clara
        cadastrar(votacaoId, admin, proposta("Hospital", "1000.01"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(containsString("maior que o orçamento total da votação")));

        // Act + Assert: até a 10ª não há aviso; a 11ª gera aviso
        for (int i = 2; i <= 10; i++) {
            cadastrar(votacaoId, admin, proposta("Proposta " + i, "10"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.avisos.length()").value(0));
        }
        cadastrar(votacaoId, admin, proposta("Proposta 11", "10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("11 propostas")));

        // Act + Assert: listagem traz orçamento, propostas e o aviso
        mvc.perform(
                        get("/api/votacoes/{id}/propostas", votacaoId)
                                .header("Authorization", bearer(eleitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamentoTotal").value(1000.00))
                .andExpect(jsonPath("$.propostas.length()").value(11))
                .andExpect(jsonPath("$.propostas[0].titulo").value("Praça nova"))
                .andExpect(jsonPath("$.avisos.length()").value(1));

        // Act + Assert: publicação
        publicar(votacaoId).andExpect(status().isNoContent());
        assertThat(votacaoRepository.findById(votacaoId).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Publicada);

        // Act + Assert: depois de publicada, nada muda
        cadastrar(votacaoId, admin, proposta("Tardia", "10")).andExpect(status().isConflict());
        definirOrcamento(votacaoId, "2000").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Orçamento participativo sem propostas não é publicado")
    void naoPublicaSemPropostas() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        configuraRegras(votacaoId);
        definirOrcamento(votacaoId, "1000").andExpect(status().isOk());

        // Act + Assert
        publicar(votacaoId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ao menos uma proposta")));
        assertThat(votacaoRepository.findById(votacaoId).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.RascunhoVotacao);
    }

    @Test
    @DisplayName("Orçamento participativo sem orçamento total não é publicado")
    void naoPublicaSemOrcamento() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        configuraRegras(votacaoId);
        cadastrarProposta(votacaoId, "Praça nova", "400");

        // Act + Assert
        publicar(votacaoId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("orçamento total")));
    }

    @Test
    @DisplayName("Edição e remoção depois da publicação são rejeitadas com 409")
    void naoEditaNemRemoveDepoisDePublicada() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        configuraRegras(votacaoId);
        definirOrcamento(votacaoId, "1000").andExpect(status().isOk());
        UUID propostaId = cadastrarProposta(votacaoId, "Praça nova", "400");
        publicar(votacaoId).andExpect(status().isNoContent());

        // Act + Assert
        mvc.perform(
                        put("/api/votacoes/{id}/propostas/{p}", votacaoId, propostaId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(proposta("Praça maior", "500")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("RascunhoVotacao")));
        mvc.perform(
                        delete("/api/votacoes/{id}/propostas/{p}", votacaoId, propostaId)
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ADMIN_VOTACAO edita e remove proposta em rascunho")
    void editaERemoveEmRascunho() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        definirOrcamento(votacaoId, "1000").andExpect(status().isOk());
        UUID propostaId = cadastrarProposta(votacaoId, "Praça nova", "400");

        // Act + Assert: edição
        mvc.perform(
                        put("/api/votacoes/{id}/propostas/{p}", votacaoId, propostaId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(proposta("Praça maior", "900")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposta.titulo").value("Praça maior"))
                .andExpect(jsonPath("$.proposta.custo").value(900));

        // Act + Assert: edição acima do orçamento
        mvc.perform(
                        put("/api/votacoes/{id}/propostas/{p}", votacaoId, propostaId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(proposta("Praça maior", "1500")))
                .andExpect(status().isConflict());

        // Act + Assert: remoção
        mvc.perform(
                        delete("/api/votacoes/{id}/propostas/{p}", votacaoId, propostaId)
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/votacoes/{id}/propostas", votacaoId)
                                .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.propostas.length()").value(0));
    }

    @Test
    @DisplayName("Reduzir o orçamento abaixo do custo de uma proposta existente é rejeitado")
    void naoReduzOrcamentoAbaixoDeProposta() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        cadastrarProposta(votacaoId, "Praça nova", "400");

        // Act + Assert
        definirOrcamento(votacaoId, "399.99")
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value(containsString("não pode ser menor que o custo")));
        definirOrcamento(votacaoId, "400").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Custo e orçamento zero ou negativos e campos em branco devolvem 400")
    void validaCamposObrigatorios() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();

        // Act + Assert
        cadastrar(votacaoId, admin, proposta("Praça", "0")).andExpect(status().isBadRequest());
        cadastrar(votacaoId, admin, proposta("Praça", "-5")).andExpect(status().isBadRequest());
        cadastrar(votacaoId, admin, proposta(" ", "10")).andExpect(status().isBadRequest());
        cadastrar(votacaoId, admin, "{\"titulo\": \"Praça\", \"custo\": 10}")
                .andExpect(status().isBadRequest());
        definirOrcamento(votacaoId, "0").andExpect(status().isBadRequest());
        definirOrcamento(votacaoId, "-1").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Título repetido na mesma votação devolve 409, ignorando maiúsculas")
    void rejeitaTituloRepetido() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();
        cadastrarProposta(votacaoId, "Praça nova", "400");

        // Act + Assert
        cadastrar(votacaoId, admin, proposta("praça NOVA", "100")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Propostas são rejeitadas em eleição privada")
    void rejeitaPropostaEmEleicaoPrivada() throws Exception {
        // Arrange
        UUID votacaoId = criaVotacao("ELEICAO_PRIVADA");

        // Act + Assert
        cadastrar(votacaoId, admin, proposta("Praça", "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("orçamento participativo")));
        definirOrcamento(votacaoId, "100").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ELEITOR lista mas não escreve; usuário sem papel recebe 403")
    void autorizacao() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();

        // Act + Assert
        cadastrar(votacaoId, eleitor, proposta("Praça", "10")).andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/votacoes/{id}/orcamento", votacaoId)
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orcamentoTotal\": 100}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/votacoes/{id}/propostas", votacaoId)
                                .header("Authorization", bearer(eleitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamentoTotal").value(nullValue()));
        mvc.perform(
                        get("/api/votacoes/{id}/propostas", votacaoId)
                                .header("Authorization", bearer(semPapel)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Editar ou remover proposta inexistente devolve 404")
    void propostaInexistente() throws Exception {
        // Arrange
        UUID votacaoId = criaOrcamentoParticipativo();

        // Act + Assert
        mvc.perform(
                        delete("/api/votacoes/{id}/propostas/{p}", votacaoId, UUID.randomUUID())
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
