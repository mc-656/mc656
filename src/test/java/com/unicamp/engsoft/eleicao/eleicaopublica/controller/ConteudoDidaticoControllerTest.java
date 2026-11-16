package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Conteúdo didático (RF-32) servido ponta a ponta pela API JSON, sem autenticação. */
@AutoConfigureMockMvc
class ConteudoDidaticoControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/eleicoes-publicas/didatico/quociente-eleitoral";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Sem token, devolve o exemplo numérico passo a passo")
    void devolveExemploSemToken() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value(containsString("quociente eleitoral")))
                .andExpect(jsonPath("$.exemplo.votosValidos").value(100_000))
                .andExpect(jsonPath("$.exemplo.vagas").value(5))
                .andExpect(jsonPath("$.exemplo.quocienteEleitoral").value(20_000))
                .andExpect(jsonPath("$.exemplo.partidos[0].sigla").value("Partido A"))
                .andExpect(jsonPath("$.exemplo.partidos[0].quocientePartidario").value(2))
                .andExpect(jsonPath("$.exemplo.partidos[0].cadeirasTotais").value(3))
                .andExpect(jsonPath("$.exemplo.partidos[1].quocientePartidario").value(1))
                .andExpect(jsonPath("$.exemplo.partidos[2].disputaSobras").value(false))
                .andExpect(jsonPath("$.exemplo.rodadasSobras.length()").value(2))
                .andExpect(jsonPath("$.exemplo.rodadasSobras[0].eleito.nome").value("Carla"))
                .andExpect(jsonPath("$.exemplo.rodadasSobras[1].eleito.nome").value("Elisa"))
                .andExpect(jsonPath("$.exemplo.eleitos.length()").value(5))
                .andExpect(
                        jsonPath("$.exemplo.casoContraIntuitivo.eleitoComMenosVotos.votos")
                                .value(5_000))
                .andExpect(
                        jsonPath("$.exemplo.casoContraIntuitivo.naoEleitoComMaisVotos.nome")
                                .value("Gabriela"))
                .andExpect(
                        jsonPath("$.exemplo.casoContraIntuitivo.naoEleitoComMaisVotos.votos")
                                .value(13_000))
                .andExpect(
                        jsonPath("$.baseLegal[*].url")
                                .value(hasItem(startsWith("https://www.planalto.gov.br/"))))
                .andExpect(jsonPath("$.secoes.length()").value(6));
    }

    @Test
    @DisplayName("Token inválido ou vencido não bloqueia o conteúdo público")
    void tokenInvalidoNaoBloqueia() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get(URL).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Só a leitura é pública: outros métodos continuam exigindo token")
    void apenasGetEhPublico() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tipos de voto: nulo e branco inválidos, legenda válida, com links")
    void tiposVotoSemToken() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get("/api/eleicoes-publicas/didatico/tipos-voto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos.length()").value(3))
                .andExpect(jsonPath("$.tipos[0].id").value("nulo"))
                .andExpect(jsonPath("$.tipos[0].contaComoValido").value(false))
                .andExpect(jsonPath("$.tipos[1].id").value("branco"))
                .andExpect(jsonPath("$.tipos[1].contaComoValido").value(false))
                .andExpect(jsonPath("$.tipos[2].id").value("legenda"))
                .andExpect(jsonPath("$.tipos[2].contaComoValido").value(true))
                .andExpect(jsonPath("$.tipos[0].comoRegistrarNaUrna").isNotEmpty())
                .andExpect(
                        jsonPath("$.legendaNoQuociente.titulo")
                                .value(containsString("quociente partidário")))
                .andExpect(
                        jsonPath("$.urlExplicacaoQuociente")
                                .value("/eleicoes-publicas/quociente-eleitoral"))
                .andExpect(jsonPath("$.fontes[*].url").value(hasItem("https://www.tse.jus.br/")));
    }

    @Test
    @DisplayName("Cargos: sete cargos com sistema, atribuições e link estável para a descrição")
    void cargosSemToken() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get("/api/eleicoes-publicas/didatico/cargos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargos.length()").value(7))
                .andExpect(jsonPath("$.cargos[6].id").value("vereador"))
                .andExpect(jsonPath("$.cargos[6].sistema").value("PROPORCIONAL"))
                .andExpect(
                        jsonPath("$.cargos[6].urlDescricao")
                                .value("/eleicoes-publicas/cargos#vereador"))
                .andExpect(jsonPath("$.cargos[0].sistema").value("MAJORITARIO"))
                .andExpect(jsonPath("$.cargos[0].atribuicoes.length()").value(3))
                .andExpect(jsonPath("$.secoes[0].id").value("chapas"))
                .andExpect(jsonPath("$.secoes[1].id").value("majoritario-proporcional"))
                .andExpect(jsonPath("$.fontes[*].url").value(hasItem("https://www.tse.jus.br/")));
    }
}
