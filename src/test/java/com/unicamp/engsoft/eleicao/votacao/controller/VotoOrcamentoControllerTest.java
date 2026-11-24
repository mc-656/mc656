package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.SaldoOrcamentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotoOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.service.RegistroVotoOrcamentoService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Testes dos endpoints REST de Orçamento Participativo (RF-21, RF-22). */
@ExtendWith(MockitoExtension.class)
class VotoOrcamentoControllerTest {

    private static final UUID VOTACAO_ID = UUID.randomUUID();

    @Mock RegistroVotoOrcamentoService registroVotoOrcamentoService;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        VotoOrcamentoController controller =
                new VotoOrcamentoController(registroVotoOrcamentoService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/votacoes/{id}/orcamento/saldo retorna o saldo do eleitor")
    void consultarSaldoRetorna200() throws Exception {
        SaldoOrcamentoResponse response =
                new SaldoOrcamentoResponse(
                        VOTACAO_ID, new BigDecimal("100.00"), new BigDecimal("100.00"), false);
        when(registroVotoOrcamentoService.consultarSaldo(eq(VOTACAO_ID), any()))
                .thenReturn(response);

        mvc.perform(get("/api/votacoes/{id}/orcamento/saldo", VOTACAO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votacaoId").value(VOTACAO_ID.toString()))
                .andExpect(jsonPath("$.saldoTotal").value(100.00))
                .andExpect(jsonPath("$.saldoDisponivel").value(100.00))
                .andExpect(jsonPath("$.jaVotou").value(false));

        verify(registroVotoOrcamentoService).consultarSaldo(eq(VOTACAO_ID), any());
    }

    @Test
    @DisplayName("POST /api/votacoes/{id}/votos-orcamento registra voto e retorna comprovante")
    void votarRetorna201EComprovante() throws Exception {
        UUID propostaId = UUID.randomUUID();
        Instant agora = Instant.parse("2026-06-01T12:00:00Z");
        ComprovanteResponse response =
                new ComprovanteResponse(VOTACAO_ID, "COMPROVANTE-ORCAMENTO-TESTE", agora);

        when(registroVotoOrcamentoService.registrar(
                        eq(VOTACAO_ID), any(VotoOrcamentoRequest.class), any()))
                .thenReturn(response);

        String json =
                """
                {
                  "alocacoes": [
                    {
                      "propostaId": "%s",
                      "pontos": 45.50
                    }
                  ]
                }
                """
                        .formatted(propostaId);

        mvc.perform(
                        post("/api/votacoes/{id}/votos-orcamento", VOTACAO_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.votacaoId").value(VOTACAO_ID.toString()))
                .andExpect(jsonPath("$.comprovante").value("COMPROVANTE-ORCAMENTO-TESTE"))
                .andExpect(jsonPath("$.emitidoEm").value("2026-06-01T12:00:00Z"));

        verify(registroVotoOrcamentoService)
                .registrar(eq(VOTACAO_ID), any(VotoOrcamentoRequest.class), any());
    }

    @Test
    @DisplayName(
            "POST /api/votacoes/{id}/votos-orcamento rejeita payload com lista de alocações vazia")
    void votarRejeitaListaVazia() throws Exception {
        String json = "{\"alocacoes\": []}";

        mvc.perform(
                        post("/api/votacoes/{id}/votos-orcamento", VOTACAO_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest());
    }
}
