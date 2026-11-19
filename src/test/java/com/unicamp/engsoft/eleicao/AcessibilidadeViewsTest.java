package com.unicamp.engsoft.eleicao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificações automatizadas de acessibilidade WCAG 2.1 AA nas telas web (RNF-07, #140): idioma,
 * título, link de salto para conteúdo principal, único {@code <h1>}, hierarquia contínua de
 * títulos, cabeçalhos de tabela com {@code scope} e associação de erros em formulários.
 */
@AutoConfigureMockMvc
@Transactional
class AcessibilidadeViewsTest extends AbstractIntegrationTest {

    private static final String ELEICAO_2026 = "00000000-0000-4000-8000-000000000002";
    private static final String MARINA = "00000000-0000-4000-8000-000000001004";
    private static final String LUCAS = "00000000-0000-4000-8000-000000001005";

    private static final Pattern CABECALHOS =
            Pattern.compile("<h([1-6])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITULO = Pattern.compile("<title>([^<]+)</title>");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Páginas públicas cumprem critérios estruturais de acessibilidade WCAG 2.1 AA")
    void paginasPublicasAcessiveis() throws Exception {
        List<String> rotasPublicas =
                List.of(
                        "/login",
                        "/cadastro",
                        "/eleicoes-publicas",
                        "/eleicoes-publicas/" + ELEICAO_2026,
                        "/eleicoes-publicas/candidatos/" + MARINA,
                        "/eleicoes-publicas/"
                                + ELEICAO_2026
                                + "/comparar?candidatos="
                                + MARINA
                                + "&candidatos="
                                + LUCAS,
                        "/eleicoes-publicas/" + ELEICAO_2026 + "/conteudos",
                        "/eleicoes-publicas/cargos",
                        "/eleicoes-publicas/tipos-voto");

        for (String rota : rotasPublicas) {
            String html =
                    mvc.perform(get(rota))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            validarEstruturaAcessivel(rota, html);
        }

        // Tabela do comparador deve declarar scope="col" e scope="row"
        String comparadorHtml =
                mvc.perform(
                                get("/eleicoes-publicas/{id}/comparar", ELEICAO_2026)
                                        .param("candidatos", MARINA, LUCAS))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(comparadorHtml)
                .contains("scope=\"col\"", "scope=\"row\"", "caption class=\"sr-only\"");
    }

    @Test
    @DisplayName("Páginas autenticadas e erros de formulário mantêm associação acessível")
    void paginasAutenticadasEFormulariosAcessiveis() throws Exception {
        Usuario usuario =
                usuarioRepository.save(
                        new Usuario(
                                "Acessibilidade",
                                "12345678909",
                                "a11y@unicamp.br",
                                "hash",
                                LocalDate.of(1992, 3, 15)));
        UsuarioAutenticado principal = UsuarioAutenticado.de(usuario);

        for (String rota : List.of("/", "/votacoes", "/votacoes/nova", "/comprovantes/verificar")) {
            String html =
                    mvc.perform(get(rota).with(user(principal)))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            validarEstruturaAcessivel(rota, html);
        }

        // Submissão inválida no cadastro deve marcar aria-invalid, aria-describedby e role="alert"
        String cadastroErroHtml =
                mvc.perform(
                                post("/cadastro")
                                        .with(csrf())
                                        .param("nome", "")
                                        .param("cpf", "000")
                                        .param("email", "invalido")
                                        .param("senha", "123"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(cadastroErroHtml)
                .contains(
                        "aria-invalid=\"true\"",
                        "aria-describedby=\"erro-nome\"",
                        "role=\"alert\"");
    }

    private static void validarEstruturaAcessivel(String rota, String html) {
        assertThat(html).as("lang pt-BR em %s", rota).contains("<html lang=\"pt-BR\"");
        Matcher titulo = TITULO.matcher(html);
        assertThat(titulo.find()).as("<title> em %s", rota).isTrue();
        assertThat(titulo.group(1)).contains("Decisão Coletiva");

        assertThat(html)
                .as("skip-link em %s", rota)
                .contains(
                        "href=\"#conteudo-principal\"",
                        "id=\"conteudo-principal\"",
                        "tabindex=\"-1\"");

        Matcher headings = CABECALHOS.matcher(html);
        List<Integer> niveis = new ArrayList<>();
        while (headings.find()) {
            niveis.add(Integer.parseInt(headings.group(1)));
        }
        assertThat(niveis).as("deve conter ao menos <h1> em %s", rota).isNotEmpty();
        assertThat(niveis.getFirst()).as("primeiro título deve ser h1 em %s", rota).isEqualTo(1);
        assertThat(niveis.stream().filter(n -> n == 1).count())
                .as("apenas um <h1> em %s", rota)
                .isEqualTo(1L);
        for (int i = 1; i < niveis.size(); i++) {
            assertThat(niveis.get(i) - niveis.get(i - 1))
                    .as("não pular nível de heading em %s (%s)", rota, niveis)
                    .isLessThanOrEqualTo(1);
        }
    }
}
