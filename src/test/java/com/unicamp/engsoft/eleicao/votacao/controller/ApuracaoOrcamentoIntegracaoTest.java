package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.SituacaoApuracaoComprovante;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fluxo ponta a ponta da apuração por cotas e desempate em Orçamento Participativo (RF-23, #130,
 * #95, #129).
 */
@AutoConfigureMockMvc
@Transactional
class ApuracaoOrcamentoIntegracaoTest extends AbstractIntegrationTest {

    private static final LocalDate NASCIMENTO = LocalDate.of(1991, 8, 20);

    @Autowired MockMvc mvc;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired VotacaoRepository votacaoRepository;
    @Autowired PapelVotacaoRepository papelRepository;
    @Autowired PropostaRepository propostaRepository;
    @Autowired VotacaoStateService stateService;
    @Autowired EntityManager entityManager;

    private Usuario admin;
    private Usuario eleitor1;
    private Usuario eleitor2;

    @BeforeEach
    void semear() {
        admin =
                usuarioRepository.save(
                        new Usuario(
                                "AdminOP",
                                "12345678909",
                                "admin-op@unicamp.br",
                                "hash",
                                NASCIMENTO));
        eleitor1 =
                usuarioRepository.save(
                        new Usuario(
                                "AnaOP", "52998224725", "ana-op@unicamp.br", "hash", NASCIMENTO));
        eleitor2 =
                usuarioRepository.save(
                        new Usuario(
                                "BetoOP", "98765432100", "beto-op@unicamp.br", "hash", NASCIMENTO));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    @Test
    @DisplayName(
            "Vota pela interface, apura cotas com desempate por menor custo, inclui no comprovante e publica")
    void fluxoCompletoOrcamentoParticipativo() throws Exception {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3600),
                        "Campus Participativo",
                        "Obras");
        votacao.definirOrcamentoTotal(new BigDecimal("100.00"));
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        votacao = votacaoRepository.save(votacao);

        papelRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelRepository.save(
                new PapelVotacao(eleitor1.getId(), votacao.getId(), PapelUsuario.ELEITOR));
        papelRepository.save(
                new PapelVotacao(eleitor2.getId(), votacao.getId(), PapelUsuario.ELEITOR));

        // Cara e Barata empatam em 60 pontos; Barata (40) vence sobre Cara (70) e sobra 60 para
        // Biblioteca (60)
        Proposta cara =
                propostaRepository.save(
                        new Proposta(
                                votacao.getId(),
                                "Ginásio",
                                "Novo ginásio",
                                new BigDecimal("70.00")));
        Proposta barata =
                propostaRepository.save(
                        new Proposta(
                                votacao.getId(),
                                "Ciclovia",
                                "Nova ciclovia",
                                new BigDecimal("40.00")));
        Proposta terceira =
                propostaRepository.save(
                        new Proposta(
                                votacao.getId(), "Biblioteca", "Acervo", new BigDecimal("60.00")));
        entityManager.flush();

        // Eleitor 1 vota via web (/votacoes/{id}/votar-orcamento)
        mvc.perform(
                        get("/votacoes/{id}/votar", votacao.getId())
                                .with(user(UsuarioAutenticado.de(eleitor1))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saldo total:")))
                .andExpect(content().string(containsString("Ciclovia")));

        var resWeb =
                mvc.perform(
                                post("/votacoes/{id}/votar-orcamento", votacao.getId())
                                        .with(user(UsuarioAutenticado.de(eleitor1)))
                                        .with(csrf())
                                        .param("propostaIds", cara.getId().toString())
                                        .param("pontos", "30")
                                        .param("propostaIds", barata.getId().toString())
                                        .param("pontos", "30")
                                        .param("propostaIds", terceira.getId().toString())
                                        .param("pontos", "10"))
                        .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/comprovante"))
                        .andExpect(flash().attributeExists("comprovante"))
                        .andReturn();
        String compWeb = (String) resWeb.getFlashMap().get("comprovante");

        // Eleitor 2 vota via API
        mvc.perform(
                        post("/api/votacoes/{id}/votos-orcamento", votacao.getId())
                                .header("Authorization", bearer(eleitor2))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"alocacoes":[
                                          {"propostaId":"%s","pontos":30},
                                          {"propostaId":"%s","pontos":30},
                                          {"propostaId":"%s","pontos":20}
                                        ]}
                                        """
                                                .formatted(
                                                        cara.getId(),
                                                        barata.getId(),
                                                        terceira.getId())))
                .andExpect(status().isCreated());

        // Encerra prazo e dispara apuração automática
        votacao.definirPeriodo(Instant.now().minusSeconds(120), Instant.now().minusSeconds(5));
        entityManager.flush();
        stateService.transitar(votacao.getId(), EstadoVotacao.Apuracao, null, "fim");
        entityManager.flush();

        // Comprovante agora confirma inclusão na apuração
        mvc.perform(
                        post("/api/comprovantes/verificacao")
                                .header("Authorization", bearer(eleitor1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comprovante\":\"%s\"}".formatted(compWeb)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.situacaoApuracao")
                                .value(SituacaoApuracaoComprovante.INCLUIDA_NA_APURACAO.name()));

        // Admin publica resultado e eleitor visualiza seleção e orçamento restante = 0
        stateService.transitar(votacao.getId(), EstadoVotacao.Encerrada, admin.getId(), null);

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", votacao.getId())
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusApuracao").value("DEFINIDO"))
                .andExpect(jsonPath("$.orcamento.orcamentoTotal").value(100.0))
                .andExpect(jsonPath("$.orcamento.orcamentoUtilizado").value(100.0))
                .andExpect(jsonPath("$.orcamento.saldoRestante").value(0.0))
                .andExpect(jsonPath("$.opcoes", hasSize(3)))
                .andExpect(jsonPath("$.opcoes[0].nome").value("Ciclovia"))
                .andExpect(jsonPath("$.opcoes[0].statusOrcamento").value("SELECIONADA"))
                .andExpect(jsonPath("$.opcoes[1].nome").value("Ginásio"))
                .andExpect(
                        jsonPath("$.opcoes[1].statusOrcamento").value("NAO_SELECIONADA_ORCAMENTO"))
                .andExpect(jsonPath("$.opcoes[2].nome").value("Biblioteca"))
                .andExpect(jsonPath("$.opcoes[2].statusOrcamento").value("SELECIONADA"));

        mvc.perform(
                        get("/votacoes/{id}/resultado", votacao.getId())
                                .with(user(UsuarioAutenticado.de(eleitor1))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Orçamento utilizado")))
                .andExpect(content().string(containsString("Selecionada")));
    }

    @Test
    @DisplayName("Orçamento participativo sem quórum mínimo é anulado automaticamente")
    void orcamentoSemQuorumAnulado() throws Exception {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.now().minusSeconds(120),
                        Instant.now().minusSeconds(5),
                        "OP Quórum",
                        "Teste");
        votacao.definirOrcamentoTotal(new BigDecimal("100.00"));
        votacao.configurarRegras(
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal("80"), false));
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        votacao = votacaoRepository.save(votacao);

        papelRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelRepository.save(
                new PapelVotacao(eleitor1.getId(), votacao.getId(), PapelUsuario.ELEITOR));
        papelRepository.save(
                new PapelVotacao(eleitor2.getId(), votacao.getId(), PapelUsuario.ELEITOR));
        propostaRepository.save(
                new Proposta(
                        votacao.getId(), "Horta", "Horta comunitária", new BigDecimal("30.00")));
        entityManager.flush();

        stateService.transitar(votacao.getId(), EstadoVotacao.Apuracao, null, "fim");
        entityManager.flush();

        assertThat(votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Anulada);

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", votacao.getId())
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Anulada"))
                .andExpect(jsonPath("$.opcoes", hasSize(0)))
                .andExpect(
                        jsonPath("$.anulacao.resumo")
                                .value(
                                        "Anulada por falta de quórum: 0 de 2 eleitores (80% exigido)"));
    }
}
