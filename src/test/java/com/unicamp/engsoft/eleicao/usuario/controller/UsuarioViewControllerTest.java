package com.unicamp.engsoft.eleicao.usuario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Telas de login e cadastro (RF-01/RF-03): renderização, validação e mensagens do domínio. */
@AutoConfigureMockMvc
@Transactional
class UsuarioViewControllerTest extends AbstractIntegrationTest {

    private static final String SENHA = "senha-secreta-123";
    private static final String EMAIL = "caio@example.com";
    private static final String CPF = "12345678909";
    private static final String OUTRO_CPF = "52998224725";

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired PasswordEncoder passwordEncoder;

    private ResultActions cadastrar(String nome, String cpf, String email, String senha)
            throws Exception {
        return mvc.perform(
                post("/cadastro")
                        .param("nome", nome)
                        .param("cpf", cpf)
                        .param("email", email)
                        .param("senha", senha)
                        .with(csrf()));
    }

    @Test
    @DisplayName("Tela de login é pública e envia e-mail, senha e token CSRF")
    void telaDeLoginRenderiza() throws Exception {
        // Act & Assert
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"senha\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @DisplayName("Tela de login mostra erro genérico após credencial inválida")
    void telaDeLoginMostraErro() throws Exception {
        // Act & Assert
        mvc.perform(get("/login").param("erro", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-mail ou senha inválidos.")));
    }

    @Test
    @DisplayName("Tela de cadastro é pública e renderiza o formulário vazio")
    void telaDeCadastroRenderiza() throws Exception {
        // Act & Assert
        mvc.perform(get("/cadastro"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"cpf\"")));
    }

    @Test
    @DisplayName("Cadastro válido persiste o usuário e redireciona para o login")
    void cadastroValidoRedirecionaParaLogin() throws Exception {
        // Act
        cadastrar("Caio", CPF, EMAIL, SENHA).andExpect(redirectedUrl("/login?cadastrado"));

        // Assert
        Usuario salvo = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(SENHA, salvo.getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("Cadastro inválido reexibe o formulário com as mensagens do Bean Validation")
    void cadastroInvalidoMostraErrosDeCampo() throws Exception {
        // Act & Assert
        cadastrar("", "123", "nao-e-email", "curta")
                .andExpect(status().isOk())
                .andExpect(view().name("cadastro"))
                .andExpect(model().attributeHasFieldErrors("cadastro", "nome", "cpf", "email"))
                .andExpect(content().string(containsString("O nome é obrigatório")))
                .andExpect(content().string(containsString("O CPF deve ser válido")))
                .andExpect(content().string(containsString("A senha deve ter entre 8 e 72")));
        assertThat(usuarioRepository.count()).isZero();
    }

    /** A view não duplica a regra: a mensagem é a mesma que o UsuarioService lança para a API. */
    @Test
    @DisplayName("E-mail repetido mostra a mensagem do domínio no formulário")
    void emailRepetidoMostraMensagemDoDominio() throws Exception {
        // Arrange
        usuarioRepository.save(new Usuario("Outro", OUTRO_CPF, EMAIL, "hash"));

        // Act & Assert
        cadastrar("Caio", CPF, EMAIL, SENHA)
                .andExpect(status().isOk())
                .andExpect(view().name("cadastro"))
                .andExpect(
                        content().string(containsString("Já existe um usuário com esse email.")));
    }

    @Test
    @DisplayName("Formulário reexibido mantém os campos preenchidos, exceto a senha")
    void reexibicaoNaoDevolveSenha() throws Exception {
        // Act & Assert
        cadastrar("Caio", "123", EMAIL, SENHA)
                .andExpect(content().string(containsString("value=\"" + EMAIL + "\"")))
                .andExpect(content().string(not(containsString(SENHA))));
    }

    @Test
    @DisplayName("Cadastro sem token CSRF é recusado")
    void cadastroSemCsrfRecusado() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/cadastro")
                                .param("nome", "Caio")
                                .param("cpf", CPF)
                                .param("email", EMAIL)
                                .param("senha", SENHA))
                .andExpect(status().isForbidden());
        assertThat(usuarioRepository.count()).isZero();
    }
}
