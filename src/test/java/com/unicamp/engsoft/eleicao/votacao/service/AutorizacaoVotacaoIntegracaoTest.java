package com.unicamp.engsoft.eleicao.votacao.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ponta a ponta, com token real: {@code @EnableMethodSecurity} ligado, o bean resolvido pelo nome
 * na SpEL e a negação saindo como 403, não 500. Usa um controller só de teste porque ainda não há
 * endpoint de votação.
 */
@AutoConfigureMockMvc
@Transactional
@Import(AutorizacaoVotacaoIntegracaoTest.ControllerProtegido.class)
class AutorizacaoVotacaoIntegracaoTest extends AbstractIntegrationTest {

    /** Aninhado direto no teste para o component scan ignorá-lo; entra só via {@code @Import}. */
    @RestController
    static class ControllerProtegido {
        @GetMapping("/teste/votacoes/{votacaoId}/admin")
        @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
        String admin(@PathVariable UUID votacaoId) {
            return "ok";
        }
    }

    @Autowired MockMvc mvc;

    @Autowired TokenService tokenService;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    private Usuario usuario;
    private Votacao votacao;

    @BeforeEach
    void semeia() {
        usuario =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2026-10-01T12:00:00Z"),
                                Instant.parse("2026-10-02T12:00:00Z"),
                                "Eleição",
                                "Descrição"));
    }

    private String bearer() {
        return "Bearer " + tokenService.gerar(UsuarioAutenticado.de(usuario));
    }

    @Test
    @DisplayName("Admin da votação passa")
    void adminPassa() throws Exception {
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(usuario.getId(), votacao.getId(), PapelUsuario.ADMIN_VOTACAO));

        mvc.perform(
                        get("/teste/votacoes/{id}/admin", votacao.getId())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Eleitor da votação recebe 403 em operação de admin")
    void eleitorRecebe403() throws Exception {
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(usuario.getId(), votacao.getId(), PapelUsuario.ELEITOR));

        mvc.perform(
                        get("/teste/votacoes/{id}/admin", votacao.getId())
                                .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem token responde 401")
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/teste/votacoes/{id}/admin", votacao.getId()))
                .andExpect(status().isUnauthorized());
    }
}
