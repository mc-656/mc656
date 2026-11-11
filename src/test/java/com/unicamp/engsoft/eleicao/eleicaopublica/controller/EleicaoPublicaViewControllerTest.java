package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Telas do módulo de eleições públicas (RF-30, RF-31, RF-33) renderizadas com o layout. */
@AutoConfigureMockMvc
class EleicaoPublicaViewControllerTest extends AbstractIntegrationTest {

    private static final String ELEICAO_EXEMPLO = "00000000-0000-4000-8000-000000000001";
    private static final String ANA_EXEMPLO = "00000000-0000-4000-8000-000000001001";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Lista de eleições mostra o seed com fonte")
    void listaEleicoes() throws Exception {
        // Act & Assert
        mvc.perform(get("/eleicoes-publicas").with(user("eleitor")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Eleições públicas · Decisão Coletiva")))
                .andExpect(content().string(containsString("Cidade Exemplo")))
                .andExpect(content().string(containsString("Fonte:")));
    }

    @Test
    @DisplayName("Página da eleição agrupa candidatos por cargo")
    void candidatosPorCargo() throws Exception {
        // Act & Assert
        mvc.perform(get("/eleicoes-publicas/{id}", ELEICAO_EXEMPLO).with(user("eleitor")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prefeito")))
                .andExpect(content().string(containsString("Vereador")))
                .andExpect(content().string(containsString("Ana Exemplo")));
    }

    @Test
    @DisplayName("Perfil mostra partido, histórico, propostas por tema, notícias e datas")
    void perfil() throws Exception {
        // Act & Assert
        mvc.perform(get("/eleicoes-publicas/candidatos/{id}", ANA_EXEMPLO).with(user("eleitor")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"viewport\"")))
                .andExpect(content().string(containsString("PEX — Partido Exemplo")))
                .andExpect(content().string(containsString("/eleicoes-publicas/cargos#prefeito")))
                .andExpect(content().string(containsString("Vereadora de Cidade Exemplo")))
                .andExpect(content().string(containsString("desde 2025")))
                .andExpect(content().string(containsString("Educação")))
                .andExpect(content().string(containsString("Jornal Exemplo")))
                .andExpect(content().string(containsString("15/10/2026")))
                .andExpect(content().string(containsString("atualizado em")));
    }

    @Test
    @DisplayName("Candidato inexistente renderiza a página de erro 404")
    void perfilInexistente() throws Exception {
        // Act & Assert
        mvc.perform(
                        get(
                                        "/eleicoes-publicas/candidatos/{id}",
                                        "00000000-0000-4000-8000-00000000ffff")
                                .with(user("eleitor")))
                .andExpect(status().isNotFound());
    }
}
