package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

/** Elegibilidade (RF-02) contra o banco real: os papéis são inseridos direto em papeis_votacao. */
@Transactional
class ElegibilidadeServiceIntegracaoTest extends AbstractIntegrationTest {

    @Autowired ElegibilidadeService elegibilidadeService;

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
                                "Eleição do condomínio",
                                "Síndico 2027"));
    }

    private Authentication autenticacaoDe(UUID usuarioId) {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "HS256")
                        .subject(usuarioId.toString())
                        .build();
        return new JwtAuthenticationToken(jwt);
    }

    private void concede(PapelUsuario papel) {
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(usuario.getId(), votacao.getId(), papel));
    }

    @Test
    @DisplayName("Usuário com papel ELEITOR na votação é validado como elegível")
    void eleitorEhElegivel() {
        // Arrange
        concede(PapelUsuario.ELEITOR);

        // Act / Assert
        assertThatCode(
                        () ->
                                elegibilidadeService.validar(
                                        votacao.getId(), autenticacaoDe(usuario.getId())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Usuário sem papel na votação é rejeitado com UsuarioNaoElegivelException (403)")
    void usuarioForaDaListaEhRejeitado() {
        // Arrange
        Authentication autenticacao = autenticacaoDe(usuario.getId());

        // Act / Assert
        assertThatThrownBy(() -> elegibilidadeService.validar(votacao.getId(), autenticacao))
                .isInstanceOf(UsuarioNaoElegivelException.class)
                .hasFieldOrPropertyWithValue("votacaoId", votacao.getId())
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Admin da votação sem papel ELEITOR não é elegível para votar")
    void adminSemPapelEleitorNaoEhElegivel() {
        // Arrange
        concede(PapelUsuario.ADMIN_VOTACAO);
        Authentication autenticacao = autenticacaoDe(usuario.getId());

        // Act / Assert
        assertThatThrownBy(() -> elegibilidadeService.validar(votacao.getId(), autenticacao))
                .isInstanceOf(UsuarioNaoElegivelException.class);
    }

    @Test
    @DisplayName("Eleitor de outra votação não é elegível nesta")
    void eleitorDeOutraVotacaoNaoEhElegivel() {
        // Arrange
        Votacao outra =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2026-10-01T12:00:00Z"),
                                Instant.parse("2026-10-02T12:00:00Z"),
                                "Outra eleição",
                                "Conselho fiscal"));
        papelVotacaoRepository.saveAndFlush(
                new PapelVotacao(usuario.getId(), outra.getId(), PapelUsuario.ELEITOR));
        Authentication autenticacao = autenticacaoDe(usuario.getId());

        // Act / Assert
        assertThatThrownBy(() -> elegibilidadeService.validar(votacao.getId(), autenticacao))
                .isInstanceOf(UsuarioNaoElegivelException.class);
    }

    @Test
    @DisplayName("Votação inexistente lança RecursoNaoEncontradoException (404)")
    void votacaoInexistente() {
        // Arrange
        UUID inexistente = UUID.randomUUID();
        Authentication autenticacao = autenticacaoDe(usuario.getId());

        // Act / Assert
        assertThatThrownBy(() -> elegibilidadeService.validar(inexistente, autenticacao))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
                .hasMessageContaining(inexistente.toString());
    }

    @Test
    @DisplayName("Sem autenticação JWT o usuário não é elegível")
    void semAutenticacaoNaoEhElegivel() {
        // Arrange
        concede(PapelUsuario.ELEITOR);

        // Act / Assert
        assertThatThrownBy(() -> elegibilidadeService.validar(votacao.getId(), null))
                .isInstanceOf(UsuarioNaoElegivelException.class);
    }
}
