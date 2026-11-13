package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.AnulacaoVotacaoService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Anulação de votação (RF-42, #53) ponta a ponta: HTTP, autorização, auditoria e banco. */
@AutoConfigureMockMvc
@Transactional
class AnulacaoVotacaoControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired TokenService tokenService;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired AnulacaoVotacaoService anulacaoService;

    private Usuario admin;

    private Usuario outro;

    @BeforeEach
    void semeiaUsuarios() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        outro =
                usuarioRepository.save(
                        new Usuario("Bia", "98765432100", "bia@example.com", "hash-da-senha"));
    }

    /**
     * Semeia a votação direto no estado pedido: levar até {@code Apuracao} pela API dependeria de
     * esperar as datas de início e fim passarem.
     */
    private Votacao votacaoDoAdmin(EstadoVotacao estado) {
        Instant fimEm = Instant.parse("2026-01-02T12:00:00Z");
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        fimEm.minusSeconds(86400),
                        fimEm,
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        votacao = votacaoRepository.save(votacao);
        papelVotacaoRepository.save(
                new PapelVotacao(admin.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));
        return votacao;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    private static String urlAnulacao(UUID votacaoId) {
        return "/api/votacoes/" + votacaoId + "/anulacao";
    }

    private static String urlTransicoes(UUID votacaoId) {
        return "/api/votacoes/" + votacaoId + "/transicoes";
    }

    private static String justificativa(String texto) {
        return """
                {"justificativa": "%s"}
                """
                .formatted(texto);
    }

    private EstadoVotacao estadoNoBanco(UUID votacaoId) {
        return votacaoRepository.findById(votacaoId).orElseThrow().getEstado();
    }

    @Test
    @DisplayName("Admin anula votação em apuração e a justificativa fica no histórico auditado")
    void anulaPorIrregularidadeEConsultaHistorico() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);

        // Act
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("Votos duplicados na urna 3")))
                .andExpect(status().isNoContent());

        // Assert
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Anulada);
        mvc.perform(get(urlTransicoes(votacao.getId())).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estadoAnterior").value("Apuracao"))
                .andExpect(jsonPath("$[0].estadoNovo").value("Anulada"))
                .andExpect(jsonPath("$[0].autorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$[0].origem").value("USUARIO"))
                .andExpect(jsonPath("$[0].motivo").value("Votos duplicados na urna 3"));
    }

    @ParameterizedTest(name = "corpo = {0}")
    @ValueSource(strings = {"{}", "{\"justificativa\": \"\"}", "{\"justificativa\": \"   \"}"})
    @DisplayName("Anulação sem justificativa devolve 400 e não altera o estado")
    void anulacaoSemJustificativa(String corpo) throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);

        // Act
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Assert
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Apuracao);
    }

    @Test
    @DisplayName("Justificativa acima de 500 caracteres devolve 400")
    void justificativaLongaDemais() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);

        // Act + Assert
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("a".repeat(501))))
                .andExpect(status().isBadRequest());
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Apuracao);
    }

    @Test
    @DisplayName("Usuário sem ADMIN_VOTACAO recebe 403 ao tentar anular")
    void naoAdminRecebe403() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);

        // Act
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(outro))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("tentativa indevida")))
                .andExpect(status().isForbidden());

        // Assert
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Apuracao);
    }

    @Test
    @DisplayName("Anular votação fora da apuração devolve 409")
    void anularForaDaApuracao() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.EmVotacao);

        // Act + Assert
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("cedo demais")))
                .andExpect(status().isConflict());
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName("Anulada é final: homologar, encerrar ou anular de novo é rejeitado com 409")
    void anuladaEhFinal() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("irregularidade")))
                .andExpect(status().isNoContent());

        // Act + Assert
        for (String destino : new String[] {"Homologada", "Encerrada"}) {
            mvc.perform(
                            post(urlTransicoes(votacao.getId()))
                                    .header("Authorization", bearer(admin))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"destino": "%s", "motivo": "tentativa"}
                                            """
                                                    .formatted(destino)))
                    .andExpect(status().isConflict());
        }
        mvc.perform(
                        post(urlAnulacao(votacao.getId()))
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(justificativa("de novo")))
                .andExpect(status().isConflict());
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Anulada);
    }

    @Test
    @DisplayName("Anulação automática por falta de quórum fica auditada com origem SISTEMA")
    void anulacaoAutomaticaPorFaltaDeQuorum() throws Exception {
        // Arrange
        Votacao votacao = votacaoDoAdmin(EstadoVotacao.Apuracao);

        // Act
        anulacaoService.anularPorFaltaDeQuorum(votacao.getId());

        // Assert
        assertThat(estadoNoBanco(votacao.getId())).isEqualTo(EstadoVotacao.Anulada);
        mvc.perform(get(urlTransicoes(votacao.getId())).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estadoAnterior").value("Apuracao"))
                .andExpect(jsonPath("$[0].estadoNovo").value("Anulada"))
                .andExpect(jsonPath("$[0].autorId").value(nullValue()))
                .andExpect(jsonPath("$[0].origem").value("SISTEMA"))
                .andExpect(jsonPath("$[0].motivo").value("quórum não atingido"));
    }
}
