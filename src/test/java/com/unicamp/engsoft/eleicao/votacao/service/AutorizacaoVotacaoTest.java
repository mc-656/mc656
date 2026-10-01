package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Regras do bean de autorização, sem banco. */
@ExtendWith(MockitoExtension.class)
class AutorizacaoVotacaoTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID VOTACAO = UUID.randomUUID();

    @Mock PapelVotacaoRepository papelVotacaoRepository;

    @InjectMocks AutorizacaoVotacao autorizacao;

    private static JwtAuthenticationToken token(String sub) {
        Jwt.Builder jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "HS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .claim("email", "caio@example.com");
        if (sub != null) {
            jwt.subject(sub);
        }
        return new JwtAuthenticationToken(jwt.build());
    }

    @Test
    @DisplayName("Consulta o papel pedido para o usuário do sub, na votação informada")
    void consultaPapelDoSub() {
        when(papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                        USUARIO, VOTACAO, PapelUsuario.ADMIN_VOTACAO))
                .thenReturn(true);

        assertThat(autorizacao.ehAdmin(VOTACAO, token(USUARIO.toString()))).isTrue();
        assertThat(autorizacao.ehEleitor(VOTACAO, token(USUARIO.toString()))).isFalse();
    }

    @Test
    @DisplayName("Nega sem consultar o banco quando não há como identificar o usuário")
    void negaSemIdentificacao() {
        assertThat(autorizacao.ehAdmin(VOTACAO, null)).isFalse();
        assertThat(
                        autorizacao.ehAdmin(
                                VOTACAO,
                                new UsernamePasswordAuthenticationToken(
                                        USUARIO.toString(), "senha")))
                .isFalse();
        assertThat(autorizacao.ehAdmin(VOTACAO, token(null))).isFalse();
        assertThat(autorizacao.ehAdmin(VOTACAO, token("caio@example.com"))).isFalse();
        assertThat(autorizacao.ehAdmin(null, token(USUARIO.toString()))).isFalse();

        verifyNoInteractions(papelVotacaoRepository);
    }

    @Test
    @DisplayName("Sem linha em papeis_votacao, nega")
    void negaSemPapel() {
        when(papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                        any(), any(), any()))
                .thenReturn(false);

        assertThat(autorizacao.ehEleitor(VOTACAO, token(USUARIO.toString()))).isFalse();
    }
}
