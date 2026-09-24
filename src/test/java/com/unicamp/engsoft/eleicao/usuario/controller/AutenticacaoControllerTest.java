package com.unicamp.engsoft.eleicao.usuario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
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

/** Login ponta a ponta: cadeia de filtros, AuthenticationManager, BCrypt e emissão do JWT. */
@AutoConfigureMockMvc
@Transactional
class AutenticacaoControllerTest extends AbstractIntegrationTest {

    private static final String SENHA = "senha-secreta-123";
    private static final String EMAIL = "caio@example.com";

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired PasswordEncoder passwordEncoder;

    @Autowired JwtDecoder jwtDecoder;

    private Usuario usuario;

    @BeforeEach
    void semeiaUsuario() {
        usuario =
                usuarioRepository.save(
                        new Usuario(
                                "Caio",
                                "12345678909",
                                EMAIL,
                                passwordEncoder.encode(SENHA),
                                PapelUsuario.ELEITOR));
    }

    private static String corpoLogin(String email, String senha) {
        return """
                {"email": "%s", "senha": "%s"}
                """
                .formatted(email, senha);
    }

    @Test
    @DisplayName("Credenciais corretas devolvem um token que o decoder aceita")
    void autenticaComCredenciaisCorretas() throws Exception {
        String corpo =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoLogin(EMAIL, SENHA)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tipo").value("Bearer"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Jwt token = jwtDecoder.decode(JsonPath.read(corpo, "$.token"));

        assertThat(token.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(token.getClaimAsStringList("papeis")).containsExactly("ROLE_ELEITOR");
    }

    @Test
    @DisplayName("Senha errada responde 401, e não 500")
    void recusaSenhaErrada() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoLogin(EMAIL, "senha-errada-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    /**
     * Guarda a decisão de segurança: a resposta para e-mail inexistente é idêntica à de senha
     * errada. Diferenciar as duas entregaria ao atacante a lista de contas cadastradas.
     */
    @Test
    @DisplayName("E-mail inexistente responde igual a senha errada")
    void naoRevelaQuaisEmailsExistem() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoLogin("ninguem@example.com", SENHA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    @DisplayName("E-mail malformado é barrado na validação, antes de consultar o banco")
    void recusaEmailMalformado() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoLogin("nao-e-email", SENHA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rota protegida sem token responde 401")
    void bloqueiaRequisicaoSemToken() throws Exception {
        mvc.perform(get("/api/rota-protegida")).andExpect(status().isUnauthorized());
    }

    /**
     * A rota não existe: o 404 é justamente a prova de que o token foi aceito e a requisição chegou
     * ao roteamento, em vez de parar em 401 na cadeia de filtros.
     *
     * <p>Também guarda a correção do handler global: antes de ele estender {@code
     * ResponseEntityExceptionHandler}, esta resposta era 500.
     */
    @Test
    @DisplayName("Com token válido a requisição passa pela cadeia de segurança")
    void aceitaRequisicaoComToken() throws Exception {
        String corpo =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoLogin(EMAIL, SENHA)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = JsonPath.read(corpo, "$.token");

        mvc.perform(get("/api/rota-protegida").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
