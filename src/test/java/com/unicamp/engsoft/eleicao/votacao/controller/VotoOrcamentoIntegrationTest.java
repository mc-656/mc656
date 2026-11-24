package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração de ponta a ponta para o voto em Orçamento Participativo (RF-21, RF-22,
 * RNF-01, RNF-02).
 */
@AutoConfigureMockMvc
@Transactional
class VotoOrcamentoIntegrationTest extends AbstractIntegrationTest {

    private static final String URL_SALDO = "/api/votacoes/{votacaoId}/orcamento/saldo";
    private static final String URL_VOTO = "/api/votacoes/{votacaoId}/votos-orcamento";
    private static final String URL_VERIFICACAO = "/api/comprovantes/verificacao";

    @Autowired MockMvc mvc;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired VotacaoRepository votacaoRepository;
    @Autowired PapelVotacaoRepository papelVotacaoRepository;
    @Autowired PropostaRepository propostaRepository;
    @Autowired ParticipacaoRepository participacaoRepository;
    @Autowired VotacaoStateService stateService;
    @Autowired JdbcTemplate jdbc;

    private Usuario admin;
    private Usuario eleitor;
    private Usuario naoEleitor;
    private Votacao votacao;
    private Proposta proposta1;
    private Proposta proposta2;

    @BeforeEach
    void setup() {
        admin = usuarioRepository.save(new Usuario("Admin", "12345678909", "admin@ex.com", "hash"));
        eleitor =
                usuarioRepository.save(
                        new Usuario("Eleitor", "98765432100", "eleitor@ex.com", "hash"));
        naoEleitor =
                usuarioRepository.save(new Usuario("Outro", "11144477735", "outro@ex.com", "hash"));

        votacao =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.parse("2020-01-01T10:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Orçamento Participativo Bairro",
                        "Obras 2099");
        votacao.definirOrcamentoTotal(new BigDecimal("100.00"));
        votacao = votacaoRepository.save(votacao);

        proposta1 =
                propostaRepository.save(
                        new Proposta(
                                votacao.getId(), "Parque Linear", "Desc", new BigDecimal("50.00")));
        proposta2 =
                propostaRepository.save(
                        new Proposta(votacao.getId(), "Ciclovia", "Desc", new BigDecimal("50.00")));

        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelVotacaoRepository.save(
                new PapelVotacao(eleitor.getId(), votacao.getId(), PapelUsuario.ELEITOR));

        stateService.transitar(votacao.getId(), EstadoVotacao.Publicada, admin.getId(), null);
        stateService.transitar(votacao.getId(), EstadoVotacao.EmVotacao, null, null);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    @Test
    @DisplayName("Eleitor autenticado consulta saldo de pontos antes e depois de votar")
    void consultaSaldo() throws Exception {
        mvc.perform(get(URL_SALDO, votacao.getId()).header("Authorization", bearer(eleitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoTotal").value(100.00))
                .andExpect(jsonPath("$.saldoDisponivel").value(100.00))
                .andExpect(jsonPath("$.jaVotou").value(false));

        // Usuário sem papel recebe 403
        mvc.perform(get(URL_SALDO, votacao.getId()).header("Authorization", bearer(naoEleitor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "Voto válido registra alocações, emite comprovante verificável e bloqueia voto duplicado")
    void votoValidoEVerificacao() throws Exception {
        String payload =
                """
                {
                  "alocacoes": [
                    {"propostaId": "%s", "pontos": 40.00},
                    {"propostaId": "%s", "pontos": 50.00}
                  ]
                }
                """
                        .formatted(proposta1.getId(), proposta2.getId());

        String resposta =
                mvc.perform(
                                post(URL_VOTO, votacao.getId())
                                        .header("Authorization", bearer(eleitor))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.comprovante").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String comprovante = com.jayway.jsonpath.JsonPath.read(resposta, "$.comprovante");

        // Saldo disponível após o voto vira 0
        mvc.perform(get(URL_SALDO, votacao.getId()).header("Authorization", bearer(eleitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoTotal").value(100.00))
                .andExpect(jsonPath("$.saldoDisponivel").value(0.00))
                .andExpect(jsonPath("$.jaVotou").value(true));

        // Comprovante é verificável no endpoint público de verificação
        mvc.perform(
                        post(URL_VERIFICACAO)
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comprovante\": \"%s\"}".formatted(comprovante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votacaoId").value(votacao.getId().toString()));

        // Segundo voto do mesmo eleitor é bloqueado com 409
        mvc.perform(
                        post(URL_VOTO, votacao.getId())
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isConflict());

        // Confere append-only e isolamento das alocações
        Long totalCedulas =
                jdbc.queryForObject(
                        "SELECT count(*) FROM cedulas_orcamento WHERE votacao_id = ?",
                        Long.class,
                        votacao.getId());
        Long totalAlocacoes =
                jdbc.queryForObject("SELECT count(*) FROM alocacoes_orcamento", Long.class);
        assertThat(totalCedulas).isEqualTo(1L);
        assertThat(totalAlocacoes).isEqualTo(2L);
    }

    @Test
    @DisplayName("Voto com soma de pontos excedendo o saldo é recusado com mensagem do excesso")
    void rejeitaExcessoDePontos() throws Exception {
        String payload =
                """
                {
                  "alocacoes": [
                    {"propostaId": "%s", "pontos": 60.00},
                    {"propostaId": "%s", "pontos": 55.00}
                  ]
                }
                """
                        .formatted(proposta1.getId(), proposta2.getId());

        mvc.perform(
                        post(URL_VOTO, votacao.getId())
                                .header("Authorization", bearer(eleitor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("excede o saldo disponível")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM participacoes", Long.class)).isZero();
        assertThat(
                        participacaoRepository.existsByUsuarioIdAndVotacaoId(
                                eleitor.getId(), votacao.getId()))
                .isFalse();
    }
}
