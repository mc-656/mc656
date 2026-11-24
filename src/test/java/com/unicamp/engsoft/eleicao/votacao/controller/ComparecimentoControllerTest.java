package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ComparecimentoControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/votacoes/{id}/comparecimento";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired TokenService tokenService;
    @Autowired VotacaoRepository votacaoRepository;
    @Autowired PapelVotacaoRepository papelRepository;
    @Autowired CandidatoOuChapaRepository candidatoRepository;
    @Autowired VotacaoStateService stateService;

    private Usuario admin;
    private Usuario bia;
    private Usuario leo;
    private Usuario estranho;
    private Votacao votacao;
    private CandidatoOuChapa chapaA;

    @BeforeEach
    void setup() {
        admin = usuarioRepository.save(new Usuario("Admin", "12345678909", "admin@ex.com", "hash"));
        bia = usuarioRepository.save(new Usuario("Bia", "52998224725", "bia@ex.com", "hash"));
        leo = usuarioRepository.save(new Usuario("Leo", "11144477735", "leo@ex.com", "hash"));
        estranho =
                usuarioRepository.save(
                        new Usuario("Estranho", "98765432100", "estranho@ex.com", "hash"));

        votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2020-01-01T10:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Conselho",
                        "Eleição");
        votacao.configurarRegras(
                new RegraVotacao(
                        TipoMaioria.MAIORIA_SIMPLES, null, false, new BigDecimal("50"), false));
        votacao = votacaoRepository.save(votacao);

        chapaA = candidatoRepository.save(new CandidatoOuChapa(votacao.getId(), "Chapa A", "A"));
        candidatoRepository.save(new CandidatoOuChapa(votacao.getId(), "Chapa B", "B"));

        papelRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        papelRepository.save(new PapelVotacao(bia.getId(), votacao.getId(), PapelUsuario.ELEITOR));
        papelRepository.save(new PapelVotacao(leo.getId(), votacao.getId(), PapelUsuario.ELEITOR));
    }

    private String bearer(Usuario u) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(u));
    }

    @Test
    @DisplayName(
            "Comparecimento atualiza após voto e nunca expõe contagem por opção ou identidade de"
                    + " eleitor")
    void atualizaAposVotoSemVazarOpcoes() throws Exception {
        stateService.transitar(votacao.getId(), EstadoVotacao.Publicada, admin.getId(), null);
        stateService.transitar(votacao.getId(), EstadoVotacao.EmVotacao, null, null);

        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParticipacoes").value(0))
                .andExpect(jsonPath("$.totalEleitoresAptos").value(2))
                .andExpect(jsonPath("$.percentualComparecimento").value(0))
                .andExpect(jsonPath("$.quorumMinimoPercentual").value(50))
                .andExpect(jsonPath("$.quorumAtingido").value(false))
                .andExpect(jsonPath("$.ranking").doesNotExist())
                .andExpect(jsonPath("$.opcoes").doesNotExist())
                .andExpect(jsonPath("$.eleitores").doesNotExist());

        mvc.perform(
                        post("/api/votacoes/{id}/votos", votacao.getId())
                                .header("Authorization", bearer(bia))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"opcaoId\": \"%s\"}".formatted(chapaA.getId())))
                .andExpect(status().isCreated());

        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(leo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParticipacoes").value(1))
                .andExpect(jsonPath("$.totalEleitoresAptos").value(2))
                .andExpect(jsonPath("$.percentualComparecimento").value(50.00))
                .andExpect(jsonPath("$.quorumAtingido").value(true))
                .andExpect(jsonPath("$.ranking").doesNotExist())
                .andExpect(jsonPath("$.votos").doesNotExist());
    }

    @Test
    @DisplayName("Bloqueia não participante (403), sem token (401) e fora de EmVotacao/Suspensa")
    void controleDeAcessoEEstado() throws Exception {
        mvc.perform(get(URL, votacao.getId())).andExpect(status().isUnauthorized());

        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(estranho)))
                .andExpect(status().isForbidden());

        mvc.perform(get(URL, votacao.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Votação inexistente retorna 404 após checagem de autorização negada ou ausente")
    void votacaoInexistenteBloqueadaPorAutorizacao() throws Exception {
        mvc.perform(get(URL, UUID.randomUUID()).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }
}
