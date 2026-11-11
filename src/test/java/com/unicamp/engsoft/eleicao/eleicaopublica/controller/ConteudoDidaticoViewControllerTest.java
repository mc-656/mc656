package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Páginas públicas de tipos de voto (#91) e de cargos e chapas (#92), parte de RF-32. */
@AutoConfigureMockMvc
class ConteudoDidaticoViewControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Página de tipos de voto é pública e explica nulo, branco e legenda")
    void paginaTiposVotoSemLogin() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get("/eleicoes-publicas/tipos-voto"))
                .andExpect(status().isOk())
                .andExpect(view().name("eleicaopublica/tipos-voto"))
                .andExpect(content().string(containsString("id=\"nulo\"")))
                .andExpect(content().string(containsString("id=\"branco\"")))
                .andExpect(content().string(containsString("id=\"legenda\"")))
                .andExpect(content().string(containsString("Como registrar na urna")))
                .andExpect(content().string(containsString("Não é voto válido")))
                .andExpect(content().string(containsString("quociente partidário")))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "href=\"/eleicoes-publicas/quociente-eleitoral\"")))
                .andExpect(content().string(containsString("href=\"https://www.tse.jus.br/\"")))
                .andExpect(content().string(containsString("width=device-width")));
    }

    @Test
    @DisplayName("Página de cargos é pública, com âncora por cargo, chapas e sistemas")
    void paginaCargosSemLogin() throws Exception {
        // Arrange / Act / Assert
        var resultado =
                mvc.perform(get("/eleicoes-publicas/cargos"))
                        .andExpect(status().isOk())
                        .andExpect(view().name("eleicaopublica/cargos"))
                        .andExpect(content().string(containsString("titular")))
                        .andExpect(content().string(containsString("vice")))
                        .andExpect(content().string(containsString("Majoritário")))
                        .andExpect(content().string(containsString("Proporcional")))
                        .andExpect(content().string(containsString("Principais atribuições")))
                        .andExpect(
                                content()
                                        .string(
                                                containsString(
                                                        "href=\"https://www.tse.jus.br/\"")));
        for (String cargo :
                new String[] {
                    "presidente",
                    "governador",
                    "senador",
                    "deputado-federal",
                    "deputado-estadual",
                    "prefeito",
                    "vereador"
                }) {
            resultado.andExpect(content().string(containsString("id=\"" + cargo + "\"")));
        }
    }

    @Test
    @DisplayName("Visitante anônimo vê os links de entrar e cadastrar, sem dados de usuário")
    void layoutAnonimo() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(get("/eleicoes-publicas/cargos"))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("Sair"))));
    }

    @Test
    @DisplayName("Só a leitura é pública: POST nas páginas não é aceito anonimamente")
    void apenasGetEhPublico() throws Exception {
        // Arrange / Act / Assert
        mvc.perform(post("/eleicoes-publicas/tipos-voto")).andExpect(status().isForbidden());
    }
}
