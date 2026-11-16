package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.EleitorService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code @ExceptionHandler} do {@link VotacaoViewController} (#158).
 *
 * <p>Pela aplicação inteira esse caminho só acontece numa corrida: a votação some entre o {@code
 * PreAuthorize} (que exige o papel de admin, apagado junto com ela) e a leitura. Por isso o teste
 * sobe só o controller, com os services mockados, e provoca a exceção direto no service. O que
 * importa verificar é que a tela responde com o status do domínio e a página de erro, e não com
 * 500.
 */
@ExtendWith(MockitoExtension.class)
class VotacaoViewControllerErroDeDominioTest {

    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock VotacaoService votacaoService;
    @Mock CandidatoOuChapaService candidatoService;
    @Mock EleitorService eleitorService;
    @Mock VotacaoStateService stateService;
    @Mock Validator validator;

    private MockMvc mvc;

    @BeforeEach
    void configura() {
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new VotacaoViewController(
                                        votacaoService,
                                        candidatoService,
                                        eleitorService,
                                        stateService,
                                        validator))
                        .build();
    }

    @Test
    @DisplayName("Votação que some antes da leitura responde 404 com a página de erro")
    void votacaoRemovidaResponde404() throws Exception {
        // Arrange
        when(votacaoService.buscar(VOTACAO))
                .thenThrow(new RecursoNaoEncontradoException("Votação", VOTACAO));

        // Act & Assert
        mvc.perform(get("/votacoes/{id}", VOTACAO))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 404));
    }

    @Test
    @DisplayName("Outro erro de domínio fora de formulário usa o status da própria exceção")
    void erroDeDominioUsaStatusDaExcecao() throws Exception {
        // Arrange
        when(votacaoService.buscar(VOTACAO))
                .thenThrow(new RegraDeNegocioException("votação em estado inesperado"));

        // Act & Assert
        mvc.perform(get("/votacoes/{id}/candidatos", VOTACAO))
                .andExpect(status().isConflict())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 409));
    }
}
