package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes integrados do módulo de eleições públicas (RF-30, RF-31, RF-33: #131, #132, #133, #135,
 * #88, #89).
 */
@AutoConfigureMockMvc
class EleicaoPublicaM4IntegrationTest extends AbstractIntegrationTest {

    private static final String ELEICAO_2028 = "00000000-0000-4000-8000-000000000001";
    private static final String ELEICAO_2026 = "00000000-0000-4000-8000-000000000002";
    private static final String ELEICAO_2022 = "00000000-0000-4000-8000-000000000003";

    private static final String ANA_2028 = "00000000-0000-4000-8000-000000001001";
    private static final String MARINA = "00000000-0000-4000-8000-000000001004";
    private static final String LUCAS = "00000000-0000-4000-8000-000000001005";
    private static final String SOFIA = "00000000-0000-4000-8000-000000001006";
    private static final String RAFAEL_SENADOR = "00000000-0000-4000-8000-000000001007";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName(
            "RF-30: listagem padrão exclui encerradas e filtros por situação e cargo funcionam")
    void filtrosSituacaoECargo() throws Exception {
        mvc.perform(get("/api/eleicoes-publicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ELEICAO_2028)))
                .andExpect(jsonPath("$[*].id", hasItem(ELEICAO_2026)))
                .andExpect(jsonPath("$[*].id", not(hasItem(ELEICAO_2022))));

        mvc.perform(get("/api/eleicoes-publicas").param("situacao", "ATIVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ELEICAO_2026)))
                .andExpect(jsonPath("$[*].id", not(hasItem(ELEICAO_2022))));

        mvc.perform(get("/api/eleicoes-publicas").param("situacao", "FUTURA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ELEICAO_2028)))
                .andExpect(jsonPath("$[*].id", not(hasItem(ELEICAO_2022))));

        mvc.perform(get("/api/eleicoes-publicas").param("situacao", "ENCERRADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ELEICAO_2022))
                .andExpect(jsonPath("$[0].situacao").value("ENCERRADA"));

        mvc.perform(get("/api/eleicoes-publicas").param("cargo", "governador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ELEICAO_2026));

        mvc.perform(
                        get("/api/eleicoes-publicas")
                                .param("situacao", "ENCERRADA")
                                .param("cargo", "prefeito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ELEICAO_2022));
    }

    @Test
    @DisplayName("Telas de eleições públicas são acessíveis sem login")
    void telasPublicasSemAutenticacao() throws Exception {
        mvc.perform(get("/eleicoes-publicas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Eleições Gerais Modelo 2026")))
                .andExpect(content().string(not(containsString("Eleição Municipal 2022"))));

        mvc.perform(get("/eleicoes-publicas/{id}", ELEICAO_2026))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marina Serra")))
                .andExpect(content().string(containsString("Comparar propostas")))
                .andExpect(content().string(containsString("Notícias e resumos de debates")));

        mvc.perform(get("/eleicoes-publicas/candidatos/{id}", MARINA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agenda de aparições")))
                .andExpect(
                        content()
                                .string(containsString("Sabatina sobre financiamento da educação")))
                .andExpect(content().string(not(containsString("Evento histórico encerrado"))));
    }

    @Test
    @DisplayName("RF-31 (#132): perfil inclui apenas agenda futura em ordem cronológica")
    void perfilAgendaFutura() throws Exception {
        mvc.perform(get("/api/eleicoes-publicas/candidatos/{id}", MARINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agenda", hasSize(1)))
                .andExpect(
                        jsonPath("$.agenda[0].titulo")
                                .value("Sabatina sobre financiamento da educação"))
                .andExpect(jsonPath("$.agenda[0].fonte.nome").value("Agência Pública Exemplo"));

        mvc.perform(get("/api/eleicoes-publicas/candidatos/{id}", ANA_2028))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agenda", hasSize(1)));
    }

    @Test
    @DisplayName("RF-31 (#89): comparador valida 2..4 candidatos do mesmo cargo e compara temas")
    void comparadorPropostasApiEView() throws Exception {
        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/comparacao", ELEICAO_2026)
                                .param("candidatos", MARINA, LUCAS, SOFIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargoNome").value("Governador"))
                .andExpect(jsonPath("$.candidatos", hasSize(3)))
                .andExpect(jsonPath("$.temas", hasSize(3)))
                .andExpect(jsonPath("$.temas[*].tema", hasItem("Educação")))
                .andExpect(jsonPath("$.temas[*].tema", hasItem("Saúde")))
                .andExpect(jsonPath("$.temas[*].tema", hasItem("Mobilidade")));

        mvc.perform(
                        get("/eleicoes-publicas/{id}/comparar", ELEICAO_2026)
                                .param("candidatos", MARINA, LUCAS))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ensino técnico integrado no estado")))
                .andExpect(content().string(containsString("Valorização salarial do magistério")));

        // Menos de 2 candidatos distintos -> 400 na API e mensagem de erro na view
        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/comparacao", ELEICAO_2026)
                                .param("candidatos", MARINA, MARINA))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        get("/eleicoes-publicas/{id}/comparar", ELEICAO_2026)
                                .param("candidatos", MARINA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("entre 2 e 4 candidatos")));

        // Cargos diferentes -> 400
        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/comparacao", ELEICAO_2026)
                                .param("candidatos", MARINA, RAFAEL_SENADOR))
                .andExpect(status().isBadRequest());

        // Candidato de outra eleição -> 400
        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/comparacao", ELEICAO_2026)
                                .param("candidatos", MARINA, ANA_2028))
                .andExpect(status().isBadRequest());

        // Mais de 4 candidatos -> 400
        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/comparacao", ELEICAO_2026)
                                .param(
                                        "candidatos",
                                        MARINA,
                                        LUCAS,
                                        SOFIA,
                                        RAFAEL_SENADOR,
                                        UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
            "RF-33 (#133, #135): notícias e debates suportam filtros, limite de tópicos e paginação")
    void conteudosFiltrosEPaginacao() throws Exception {
        mvc.perform(get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.itens", hasSize(3)));

        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("tipo", "RESUMO_DEBATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].temasDebate", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].temasDebate[0].topicos", hasSize(5)))
                .andExpect(
                        jsonPath(
                                "$.itens[0].temasDebate[0].topicos.length()", lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.itens[0].fonte.nome").isNotEmpty());

        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("candidatoId", MARINA)
                                .param("tipo", "NOTICIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(
                        jsonPath("$.itens[0].titulo")
                                .value("Marina Serra apresenta plano de ensino técnico integrado"));

        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("tema", "Saúde")
                                .param("size", "2")
                                .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.itens", hasSize(2)));

        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("size", "2")
                                .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.itens", hasSize(1)));

        mvc.perform(
                        get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("size", "10")
                                .param("page", "1000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.itens", hasSize(0)));

        mvc.perform(get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026).param("page", "-1"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/eleicoes-publicas/{id}/conteudos", ELEICAO_2026).param("size", "51"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/eleicoes-publicas/{id}/conteudos", ELEICAO_2026).param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não pode ser negativo")));

        mvc.perform(get("/eleicoes-publicas/{id}/conteudos", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/eleicoes-publicas/{id}/comparar", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        mvc.perform(
                        get("/eleicoes-publicas/{id}/conteudos", ELEICAO_2026)
                                .param("size", "1")
                                .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Página 1 de 3")))
                .andExpect(content().string(containsString("Próxima")));
    }
}
