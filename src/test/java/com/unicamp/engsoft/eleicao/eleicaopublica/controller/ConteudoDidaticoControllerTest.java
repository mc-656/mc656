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

/** Explicação do quociente eleitoral (RF-32) servida ponta a ponta, sem autenticação. */
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
}
