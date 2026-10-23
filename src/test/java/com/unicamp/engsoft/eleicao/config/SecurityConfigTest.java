package com.unicamp.engsoft.eleicao.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Divisão entre as duas cadeias de segurança (SPECS §7.3): API com JWT e sem CSRF, views com
 * sessão, form login e CSRF.
 */
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest extends AbstractIntegrationTest {

    private static final String SENHA = "senha-secreta-123";
    private static final String EMAIL = "caio@example.com";

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", EMAIL, passwordEncoder.encode(SENHA)));
    }

    /**
     * Guarda a ordem das cadeias: se a das views capturasse /api/**, um cliente da API receberia
     * 302 para a tela de login em vez de 401.
     */
    @Test
    @DisplayName("API sem token responde 401 em vez de redirecionar para o login")
    void apiSemTokenRespondeNaoAutorizado() throws Exception {
        // Act & Assert
        mvc.perform(post("/api/votacoes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @DisplayName("API continua aceitando POST sem token CSRF")
    void apiNaoExigeCsrf() throws Exception {
        // Arrange
        String corpo =
                """
                {"nome": "Bia", "cpf": "52998224725", "email": "bia@example.com", "senha": "%s"}
                """
                        .formatted(SENHA);

        // Act & Assert
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Página protegida sem login redireciona para /login")
    void viewAnonimaRedirecionaParaLogin() throws Exception {
        // Act & Assert
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /** O cookie de sessão é anexado pelo navegador sozinho: sem CSRF, outro site faria o POST. */
    @Test
    @DisplayName("POST nas views sem token CSRF é recusado com 403")
    void viewExigeCsrf() throws Exception {
        // Act & Assert
        mvc.perform(post("/logout").with(user(UsuarioAutenticado.de(usuario))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Form login com credenciais válidas autentica e redireciona para o início")
    void formLoginValidoAutentica() throws Exception {
        // Act & Assert
        mvc.perform(post("/login").param("email", EMAIL).param("senha", SENHA).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    @DisplayName("Form login com senha errada redireciona para /login?erro")
    void formLoginInvalidoRedirecionaComErro() throws Exception {
        // Act & Assert
        mvc.perform(post("/login").param("email", EMAIL).param("senha", "errada").with(csrf()))
                .andExpect(redirectedUrl("/login?erro"));
    }

    /** Prova que o layout decora a página e que o dialeto sec: enxerga o usuário da sessão. */
    @Test
    @DisplayName("Página inicial autenticada renderiza o layout com o e-mail do usuário")
    void inicioAutenticadoRenderizaLayout() throws Exception {
        // Act & Assert
        mvc.perform(get("/").with(user(UsuarioAutenticado.de(usuario))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Início · Decisão Coletiva")))
                .andExpect(content().string(Matchers.containsString(EMAIL)))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")));
    }
}
