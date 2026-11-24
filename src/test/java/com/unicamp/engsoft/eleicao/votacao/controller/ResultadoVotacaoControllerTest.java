package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.SituacaoApuracaoComprovante;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.ComprovanteService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicação, homologação, anulação com justificativa e inclusão no comprovante (#52, #83, #84,
 * #85).
 */
@AutoConfigureMockMvc
@Transactional
class ResultadoVotacaoControllerTest extends AbstractIntegrationTest {

    private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);

    @Autowired MockMvc mvc;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired VotacaoRepository votacaoRepository;
    @Autowired PapelVotacaoRepository papelRepository;
    @Autowired CandidatoOuChapaRepository candidatoRepository;
    @Autowired CedulaRepository cedulaRepository;
    @Autowired ParticipacaoRepository participacaoRepository;
    @Autowired ApuracaoRepository apuracaoRepository;
    @Autowired VotacaoStateService stateService;
    @Autowired ComprovanteService comprovanteService;
    @Autowired EntityManager entityManager;

    private Usuario admin;
    private Usuario eleitor1;
    private Usuario eleitor2;
    private Usuario eleitor3;
    private Usuario eleitor4;
    private Usuario estranho;

    @BeforeEach
    void semearUsuarios() {
        admin =
                usuarioRepository.save(
                        new Usuario(
                                "Admin",
                                "12345678909",
                                "admin-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
        eleitor1 =
                usuarioRepository.save(
                        new Usuario(
                                "Eleitor1",
                                "52998224725",
                                "e1-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
        eleitor2 =
                usuarioRepository.save(
                        new Usuario(
                                "Eleitor2",
                                "98765432100",
                                "e2-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
        eleitor3 =
                usuarioRepository.save(
                        new Usuario(
                                "Eleitor3",
                                "11144477735",
                                "e3-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
        eleitor4 =
                usuarioRepository.save(
                        new Usuario(
                                "Eleitor4",
                                "39053344705",
                                "e4-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
        estranho =
                usuarioRepository.save(
                        new Usuario(
                                "Estranho",
                                "47811442094",
                                "outro-res@unicamp.br",
                                "hash",
                                NASCIMENTO));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private record CenarioVotacao(
            Votacao votacao, CandidatoOuChapa chapaA, CandidatoOuChapa chapaB) {}

    private CenarioVotacao criarEmVotacao(RegraVotacao regra, List<Usuario> eleitores) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3600),
                        "Conselho 2026",
                        "Eleição oficial");
        votacao.configurarRegras(regra);
        votacao.alterarEstado(EstadoVotacao.EmVotacao);
        votacao = votacaoRepository.save(votacao);

        papelRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        for (Usuario e : eleitores) {
            papelRepository.save(
                    new PapelVotacao(e.getId(), votacao.getId(), PapelUsuario.ELEITOR));
        }

        CandidatoOuChapa chapaA =
                candidatoRepository.save(
                        new CandidatoOuChapa(votacao.getId(), "Chapa A", "Descrição A"));
        CandidatoOuChapa chapaB =
                candidatoRepository.save(
                        new CandidatoOuChapa(votacao.getId(), "Chapa B", "Descrição B"));
        entityManager.flush();
        return new CenarioVotacao(votacao, chapaA, chapaB);
    }

    private String votar(Votacao votacao, Usuario eleitor, CandidatoOuChapa chapa)
            throws Exception {
        String body =
                mvc.perform(
                                post("/api/votacoes/{id}/votos", votacao.getId())
                                        .header("Authorization", bearer(eleitor))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"opcaoId\":\"%s\"}".formatted(chapa.getId())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(body, "$.comprovante");
    }

    private void iniciarApuracao(Votacao votacao) {
        Instant agora = Instant.now();
        votacao.definirPeriodo(agora.minusSeconds(120), agora.minusSeconds(5));
        entityManager.flush();
        stateService.transitar(votacao.getId(), EstadoVotacao.Apuracao, null, "prazo encerrado");
        entityManager.flush();
    }

    private void verificarComprovante(
            Usuario usuario, String comprovante, SituacaoApuracaoComprovante esperada)
            throws Exception {
        mvc.perform(
                        post("/api/comprovantes/verificacao")
                                .header("Authorization", bearer(usuario))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comprovante\":\"%s\"}".formatted(comprovante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoApuracao").value(esperada.name()));
    }

    @Test
    @DisplayName(
            "Ciclo completo: voto NAO_CONFIRMADA -> Apuracao INCLUIDA_NA_APURACAO (prévia admin,"
                    + " 409 eleitor) -> Encerrada -> Homologada")
    void cicloCompletoPublicacaoEHomologacao() throws Exception {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        CenarioVotacao cenario = criarEmVotacao(regra, List.of(eleitor1, eleitor2, eleitor3));
        UUID id = cenario.votacao().getId();

        String comp1 = votar(cenario.votacao(), eleitor1, cenario.chapaA());
        votar(cenario.votacao(), eleitor2, cenario.chapaA());
        votar(cenario.votacao(), eleitor3, cenario.chapaB());

        // Antes da apuração: comprovante pendente
        verificarComprovante(eleitor1, comp1, SituacaoApuracaoComprovante.NAO_CONFIRMADA);

        // Encerra por sistema -> roda apuração síncrona
        iniciarApuracao(cenario.votacao());

        // Comprovante agora confirma inclusão sem vazar opção votada
        verificarComprovante(eleitor1, comp1, SituacaoApuracaoComprovante.INCLUIDA_NA_APURACAO);

        // Em Apuracao: admin vê prévia (#84), eleitor recebe 409, estranho recebe 403
        mvc.perform(get("/api/votacoes/{id}/resultado", id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Apuracao"))
                .andExpect(jsonPath("$.vencedorNome").value("Chapa A"))
                .andExpect(jsonPath("$.opcoes", hasSize(2)))
                .andExpect(jsonPath("$.homologacao").value(nullValue()));

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isConflict());

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(estranho)))
                .andExpect(status().isForbidden());

        // Admin publica resultado: Apuracao -> Encerrada
        mvc.perform(
                        post("/api/votacoes/{id}/transicoes", id)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"destino\":\"Encerrada\"}"))
                .andExpect(status().isNoContent());

        // Eleitor passa a visualizar resultado oficial
        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Encerrada"))
                .andExpect(jsonPath("$.statusApuracao").value("DEFINIDO"))
                .andExpect(jsonPath("$.vencedorNome").value("Chapa A"))
                .andExpect(jsonPath("$.opcoes[0].totalVotos").value(2))
                .andExpect(jsonPath("$.opcoes[0].vencedora").value(true));

        // Admin homologa: Encerrada -> Homologada
        mvc.perform(
                        post("/api/votacoes/{id}/transicoes", id)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"destino\":\"Homologada\",\"motivo\":\"Ata aprovada\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Homologada"))
                .andExpect(
                        jsonPath("$.homologacao.homologadaPorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.homologacao.homologadaEm").isNotEmpty())
                .andExpect(jsonPath("$.homologacao.motivo").value("Ata aprovada"));
    }

    @Test
    @DisplayName("Segundo turno expõe segundoTurnoId e permite configurar período e publicar")
    void segundoTurnoConfiguraPeriodoEPublica() throws Exception {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false);
        CenarioVotacao cenario = criarEmVotacao(regra, List.of(eleitor1, eleitor2));
        UUID id = cenario.votacao().getId();

        votar(cenario.votacao(), eleitor1, cenario.chapaA());
        votar(cenario.votacao(), eleitor2, cenario.chapaB());

        iniciarApuracao(cenario.votacao());
        stateService.transitar(id, EstadoVotacao.Encerrada, admin.getId(), null);
        entityManager.flush();

        String resposta =
                mvc.perform(
                                get("/api/votacoes/{id}/resultado", id)
                                        .header("Authorization", bearer(admin)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.statusApuracao").value("SEGUNDO_TURNO"))
                        .andExpect(jsonPath("$.finalistasSegundoTurno", hasSize(2)))
                        .andExpect(jsonPath("$.segundoTurnoId").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String segundoTurnoId = JsonPath.read(resposta, "$.segundoTurnoId");

        String inicio = Instant.now().plusSeconds(3600).toString();
        String fim = Instant.now().plusSeconds(7200).toString();
        mvc.perform(
                        put("/api/votacoes/{id}/periodo", segundoTurnoId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"inicioEm": "%s", "fimEm": "%s"}
                                        """
                                                .formatted(inicio, fim)))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/votacoes/{id}/transicoes", segundoTurnoId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"destino\":\"Publicada\"}"))
                .andExpect(status().isNoContent());

        assertThat(
                        votacaoRepository
                                .findById(UUID.fromString(segundoTurnoId))
                                .orElseThrow()
                                .getEstado())
                .isEqualTo(EstadoVotacao.Publicada);
    }

    @Test
    @DisplayName("Anulação automática por falta de quórum exibe resumo exato e omite opções (#85)")
    void anulacaoPorFaltaDeQuorum() throws Exception {
        RegraVotacao regra =
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal("50"), false);
        CenarioVotacao cenario =
                criarEmVotacao(regra, List.of(eleitor1, eleitor2, eleitor3, eleitor4));
        UUID id = cenario.votacao().getId();

        String comp = votar(cenario.votacao(), eleitor1, cenario.chapaA());

        iniciarApuracao(cenario.votacao());

        assertThat(votacaoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Anulada);

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Anulada"))
                .andExpect(jsonPath("$.vencedorId").value(nullValue()))
                .andExpect(jsonPath("$.opcoes", hasSize(0)))
                .andExpect(
                        jsonPath("$.anulacao.resumo")
                                .value(
                                        "Anulada por falta de quórum: 1 de 4 eleitores (50% exigido)"));

        verificarComprovante(eleitor1, comp, SituacaoApuracaoComprovante.VOTACAO_ANULADA);
    }

    @Test
    @DisplayName("Anulação manual por irregularidade registra justificativa e omite opções (#85)")
    void anulacaoManualPorIrregularidade() throws Exception {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        CenarioVotacao cenario = criarEmVotacao(regra, List.of(eleitor1));
        UUID id = cenario.votacao().getId();

        String comp = votar(cenario.votacao(), eleitor1, cenario.chapaA());
        iniciarApuracao(cenario.votacao());

        mvc.perform(
                        post("/api/votacoes/{id}/anulacao", id)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"justificativa\":\"Urna comprometida\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/votacoes/{id}/resultado", id)
                                .header("Authorization", bearer(eleitor1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Anulada"))
                .andExpect(jsonPath("$.vencedorId").value(nullValue()))
                .andExpect(jsonPath("$.opcoes", hasSize(0)))
                .andExpect(jsonPath("$.anulacao.origem").value("USUARIO"))
                .andExpect(jsonPath("$.anulacao.justificativa").value("Urna comprometida"))
                .andExpect(
                        jsonPath("$.anulacao.resumo").value(containsString("Urna comprometida")));

        verificarComprovante(eleitor1, comp, SituacaoApuracaoComprovante.VOTACAO_ANULADA);
    }

    @Test
    @DisplayName(
            "Sem snapshot em apuracoes, comprovante permanece NAO_CONFIRMADA mesmo em Encerrada")
    void semSnapshotPermaneceNaoConfirmada() throws Exception {
        RegraVotacao regra =
                new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false);
        CenarioVotacao cenario = criarEmVotacao(regra, List.of(eleitor1));
        String comp = votar(cenario.votacao(), eleitor1, cenario.chapaA());

        // Força estado sem persistir Apuracao (simula falha/rollback antes do snapshot)
        cenario.votacao().alterarEstado(EstadoVotacao.Encerrada);
        entityManager.flush();

        assertThat(apuracaoRepository.existsByVotacaoId(cenario.votacao().getId())).isFalse();
        verificarComprovante(eleitor1, comp, SituacaoApuracaoComprovante.NAO_CONFIRMADA);
    }
}
