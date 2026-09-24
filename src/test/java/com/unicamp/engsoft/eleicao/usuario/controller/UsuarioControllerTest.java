package com.unicamp.engsoft.eleicao.usuario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro ponta a ponta (RF-01): validação, persistência e o que sai na resposta. */
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest extends AbstractIntegrationTest {

    private static final String SENHA = "senha-secreta-123";
    private static final String EMAIL = "caio@example.com";
    private static final String CPF = "12345678909";
    private static final String OUTRO_CPF = "52998224725";

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired PasswordEncoder passwordEncoder;

    @Autowired JwtDecoder jwtDecoder;

    private static String corpoCadastro(String cpf, String email) {
        return """
                {"nome": "Caio", "cpf": "%s", "email": "%s", "senha": "%s"}
                """
                .formatted(cpf, email, SENHA);
    }

    private org.springframework.test.web.servlet.ResultActions cadastrar(String corpo)
            throws Exception {
        return mvc.perform(
                post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    @DisplayName("Cadastro bem-sucedido responde 201")
    void respondeCriadoEmCasoDeSucesso() throws Exception {
        cadastrar(corpoCadastro(CPF, EMAIL)).andExpect(status().isCreated());
    }

    /**
     * Guarda o vazamento: a entidade tem getters para tudo, então devolvê-la publicaria o hash da
     * senha e o CPF. A resposta precisa vir do UsuarioResponse.
     */
    @Test
    @DisplayName("A resposta não expõe senhaHash nem CPF")
    void naoExpoeSenhaNemCpfNaResposta() throws Exception {
        cadastrar(corpoCadastro(CPF, EMAIL))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.id").exists());
    }

    /**
     * Guarda o escalonamento de privilégio: se alguém trocar o DTO de entrada pela entidade, este
     * POST passa a criar um administrador de votação e o teste quebra.
     */
    @Test
    @DisplayName("Papel enviado no JSON é ignorado; todo cadastro sai ELEITOR")
    void ignoraPapelEnviadoPeloCliente() throws Exception {
        String corpo =
                """
                {"nome": "Caio", "cpf": "%s", "email": "%s", "senha": "%s",
                 "papel": "ADMIN_VOTACAO", "id": "00000000-0000-0000-0000-000000000001"}
                """
                        .formatted(CPF, EMAIL, SENHA);

        cadastrar(corpo).andExpect(jsonPath("$.papel").value("ELEITOR"));

        Usuario salvo = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(salvo.getPapel()).isEqualTo(PapelUsuario.ELEITOR);
    }

    @Test
    @DisplayName("A senha é gravada como hash BCrypt, nunca em texto plano")
    void gravaSenhaHasheada() throws Exception {
        cadastrar(corpoCadastro(CPF, EMAIL));

        Usuario salvo = usuarioRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(salvo.getSenhaHash()).isNotEqualTo(SENHA);
        assertThat(passwordEncoder.matches(SENHA, salvo.getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("Payload inválido é barrado pela validação com 400")
    void recusaPayloadInvalido() throws Exception {
        cadastrar(corpoCadastro(CPF, "nao-e-email")).andExpect(status().isBadRequest());
        cadastrar(
                        """
                {"nome": "", "cpf": "%s", "email": "%s", "senha": "curta"}
                """
                                .formatted(CPF, EMAIL))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E-mail já cadastrado responde 409, e não 500")
    void recusaEmailDuplicado() throws Exception {
        usuarioRepository.save(
                new Usuario(
                        "Caio", CPF, EMAIL, passwordEncoder.encode(SENHA), PapelUsuario.ELEITOR));

        cadastrar(corpoCadastro(OUTRO_CPF, EMAIL)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CPF já cadastrado responde 409, e não 500")
    void recusaCpfDuplicado() throws Exception {
        usuarioRepository.save(
                new Usuario(
                        "Caio", CPF, EMAIL, passwordEncoder.encode(SENHA), PapelUsuario.ELEITOR));

        cadastrar(corpoCadastro(CPF, "outro@example.com")).andExpect(status().isConflict());
    }

    /**
     * O único teste que pega incompatibilidade entre o BCrypt do cadastro e o da autenticação: se
     * os dois lados divergirem, o usuário se cadastra e nunca consegue entrar.
     */
    @Test
    @DisplayName("Quem se cadastra pela API consegue fazer login em seguida")
    void cadastraEDepoisAutentica() throws Exception {
        cadastrar(corpoCadastro(CPF, EMAIL));

        String corpo =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email": "%s", "senha": "%s"}
                                                """
                                                        .formatted(EMAIL, SENHA)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Jwt token = jwtDecoder.decode(JsonPath.read(corpo, "$.token"));

        Usuario salvo = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(token.getSubject()).isEqualTo(salvo.getId().toString());
        assertThat(token.getClaimAsStringList("papeis")).containsExactly("ROLE_ELEITOR");
    }
}
