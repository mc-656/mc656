package com.unicamp.engsoft.eleicao.eleicaopublica.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consulta de eleições públicas, candidatos e perfil (RF-30, RF-31, RF-33) ponta a ponta, sem
 * token, sobre o seed de exemplo da V15.
 */
@AutoConfigureMockMvc
class EleicaoPublicaControllerTest extends AbstractIntegrationTest {

    private static final String ELEICAO_EXEMPLO = "00000000-0000-4000-8000-000000000001";
    private static final String ANA_EXEMPLO = "00000000-0000-4000-8000-000000001001";
    private static final String BRUNO_MODELO = "00000000-0000-4000-8000-000000001002";
    private static final String INEXISTENTE = "00000000-0000-4000-8000-00000000ffff";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Sem token, lista as eleições públicas com fonte e data de atualização")
    void listaEleicoesSemToken() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(ELEICAO_EXEMPLO)))
                .andExpect(jsonPath("$[0].nome").value(notNullValue()))
                .andExpect(jsonPath("$[0].dataPrimeiroTurno").value("2028-10-01"))
                .andExpect(jsonPath("$[0].fonte.nome").value(notNullValue()))
                .andExpect(jsonPath("$[0].fonte.atualizadoEm").value(notNullValue()));
    }

    @Test
    @DisplayName("Lista candidatos agrupados por cargo, na ordem da urna")
    void listaCandidatosPorCargo() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas/{id}/candidatos", ELEICAO_EXEMPLO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eleicao.id").value(ELEICAO_EXEMPLO))
                .andExpect(jsonPath("$.cargos.length()").value(2))
                .andExpect(jsonPath("$.cargos[0].nome").value("Prefeito"))
                .andExpect(jsonPath("$.cargos[0].candidatos.length()").value(2))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].nomeUrna").value("Ana Exemplo"))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].partidoSigla").value("PEX"))
                .andExpect(jsonPath("$.cargos[0].candidatos[0].fonte.url").value(notNullValue()))
                .andExpect(jsonPath("$.cargos[0].candidatos[1].nomeUrna").value("Bruno Modelo"))
                .andExpect(jsonPath("$.cargos[1].nome").value("Vereador"))
                .andExpect(jsonPath("$.cargos[1].candidatos[0].nomeUrna").value("Carla Teste"));
    }

    @Test
    @DisplayName("Perfil traz partido, cargo, histórico, propostas por tema e notícias com fontes")
    void perfilCompleto() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas/candidatos/{id}", ANA_EXEMPLO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeUrna").value("Ana Exemplo"))
                .andExpect(jsonPath("$.partidoSigla").value("PEX"))
                .andExpect(jsonPath("$.partidoNome").value("Partido Exemplo (fictício)"))
                .andExpect(jsonPath("$.cargoDisputado.nome").value("Prefeito"))
                .andExpect(jsonPath("$.cargoDisputado.idDidatico").value("prefeito"))
                .andExpect(jsonPath("$.cargoDisputado.eleicaoId").value(ELEICAO_EXEMPLO))
                .andExpect(jsonPath("$.fonte.nome").value(notNullValue()))
                // histórico do mais recente para o mais antigo; mandato em curso sem ano de fim
                .andExpect(jsonPath("$.cargosAnteriores.length()").value(2))
                .andExpect(jsonPath("$.cargosAnteriores[0].anoInicio").value(2025))
                .andExpect(jsonPath("$.cargosAnteriores[0].anoFim").doesNotExist())
                .andExpect(jsonPath("$.cargosAnteriores[0].fonte.url").doesNotExist())
                .andExpect(jsonPath("$.cargosAnteriores[1].anoFim").value(2024))
                .andExpect(
                        jsonPath("$.cargosAnteriores[1].fonte.atualizadoEm")
                                .value("2026-11-01T12:00:00Z"))
                // propostas agrupadas por tema, em ordem alfabética
                .andExpect(jsonPath("$.propostasPorTema.length()").value(2))
                .andExpect(jsonPath("$.propostasPorTema[0].tema").value("Educação"))
                .andExpect(jsonPath("$.propostasPorTema[0].propostas.length()").value(2))
                .andExpect(
                        jsonPath("$.propostasPorTema[0].propostas[0].fonte.nome")
                                .value(notNullValue()))
                .andExpect(jsonPath("$.propostasPorTema[1].tema").value("Mobilidade"))
                // notícias com veículo e datas
                .andExpect(jsonPath("$.noticias.length()").value(1))
                .andExpect(jsonPath("$.noticias[0].publicadaEm").value("2026-10-15"))
                .andExpect(
                        jsonPath("$.noticias[0].fonte.nome")
                                .value("Jornal Exemplo (veículo fictício)"))
                .andExpect(
                        jsonPath("$.noticias[0].fonte.atualizadoEm").value("2026-10-15T18:00:00Z"));
    }

    @Test
    @DisplayName("Perfil sem histórico nem notícias devolve listas vazias")
    void perfilSemHistoricoNemNoticias() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas/candidatos/{id}", BRUNO_MODELO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargosAnteriores.length()").value(0))
                .andExpect(jsonPath("$.noticias.length()").value(0))
                .andExpect(jsonPath("$.propostasPorTema[0].tema").value("Saúde"));
    }

    @Test
    @DisplayName("Candidato inexistente responde 404 em ProblemDetail")
    void candidatoInexistente() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas/candidatos/{id}", INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(notNullValue()));
    }

    @Test
    @DisplayName("Eleição inexistente responde 404 em ProblemDetail")
    void eleicaoInexistente() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas/{id}/candidatos", INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Token inválido ou vencido não bloqueia a consulta pública")
    void tokenInvalidoNaoBloqueia() throws Exception {
        // Act & Assert
        mvc.perform(get("/api/eleicoes-publicas").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Só a leitura é pública: escrita no módulo continua exigindo token")
    void apenasGetEhPublico() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/api/eleicoes-publicas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/eleicoes-publicas/candidatos/{id}", ANA_EXEMPLO))
                .andExpect(status().isUnauthorized());
    }
}
