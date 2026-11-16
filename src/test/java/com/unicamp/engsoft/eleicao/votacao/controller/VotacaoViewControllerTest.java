package com.unicamp.engsoft.eleicao.votacao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Telas dos fluxos de votação do M1 (#67): renderização, envio válido redirecionando (PRG) e envio
 * inválido mostrando a mensagem do domínio.
 */
@AutoConfigureMockMvc
@Transactional
class VotacaoViewControllerTest extends AbstractIntegrationTest {

    private static final LocalDate NASCIMENTO = LocalDate.of(1990, 1, 1);

    @Autowired MockMvc mvc;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired CandidatoOuChapaRepository candidatoRepository;

    @Autowired VotacaoService votacaoService;

    @Autowired CandidatoOuChapaService candidatoService;

    private Usuario admin;

    @BeforeEach
    void semeiaAdministrador() {
        admin =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash", NASCIMENTO));
    }

    private RequestPostProcessor logado(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private Votacao rascunho() {
        return votacaoService.criarVotacao(
                new CriarVotacaoRequest(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.parse("2099-10-01T12:00:00Z"),
                        Instant.parse("2099-10-02T12:00:00Z"),
                        "Conselho 2099",
                        "Eleição do conselho"),
                admin.getId());
    }

    private Votacao prontaParaPublicar() {
        Votacao votacao = rascunho();
        votacaoService.configurarRegras(
                votacao.getId(),
                new ConfigurarRegrasRequest(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false));
        candidatoService.cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa A", null));
        candidatoService.cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa B", null));
        return votacao;
    }

    @Test
    @DisplayName("Telas de votação exigem login")
    void exigemLogin() throws Exception {
        // Act & Assert
        mvc.perform(get("/votacoes")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Início tem link para as votações do usuário")
    void inicioLevaAsVotacoes() throws Exception {
        // Act & Assert
        mvc.perform(get("/").with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/votacoes\"")));
    }

    @Test
    @DisplayName("Lista mostra só as votações que o usuário administra")
    void listaVotacoesAdministradas() throws Exception {
        // Arrange
        rascunho();
        Usuario outro =
                usuarioRepository.save(
                        new Usuario("Bia", "52998224725", "bia@example.com", "hash", NASCIMENTO));

        // Act & Assert
        mvc.perform(get("/votacoes").with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conselho 2099")));
        mvc.perform(get("/votacoes").with(logado(outro)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Conselho 2099"))));
    }

    @Test
    @DisplayName("Formulário de criação renderiza com rótulos e token CSRF")
    void formularioCriacaoRenderiza() throws Exception {
        // Act & Assert
        mvc.perform(get("/votacoes/nova").with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<label for=\"inicioEm\"")))
                .andExpect(content().string(containsString("type=\"datetime-local\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @DisplayName("Criação válida persiste, torna o autor admin e redireciona para as regras")
    void criacaoValidaRedireciona() throws Exception {
        // Act
        String destino =
                mvc.perform(
                                post("/votacoes")
                                        .with(logado(admin))
                                        .with(csrf())
                                        .param("nome", "Conselho")
                                        .param("descricao", "Eleição do conselho")
                                        .param("tipo", "ELEICAO_PRIVADA")
                                        .param("inicioEm", "2099-10-01T09:00")
                                        .param("fimEm", "2099-10-02T09:00"))
                        .andExpect(redirectedUrlPattern("/votacoes/*/regras"))
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        // Assert
        UUID id = UUID.fromString(destino.split("/")[2]);
        Votacao salva = votacaoRepository.findById(id).orElseThrow();
        assertThat(salva.getInicioEm()).isEqualTo(Instant.parse("2099-10-01T12:00:00Z"));
        assertThat(
                        papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                admin.getId(), id, PapelUsuario.ADMIN_VOTACAO))
                .isTrue();
    }

    @Test
    @DisplayName("Criação com fim antes do início mostra a mensagem do domínio")
    void criacaoComDatasInvertidasMostraMensagem() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/votacoes")
                                .with(logado(admin))
                                .with(csrf())
                                .param("nome", "Conselho")
                                .param("descricao", "Eleição do conselho")
                                .param("tipo", "ELEICAO_PRIVADA")
                                .param("inicioEm", "2099-10-02T09:00")
                                .param("fimEm", "2099-10-01T09:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/nova"))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "A data de término deve ser posterior a data de"
                                                        + " início")));
        assertThat(votacaoRepository.count()).isZero();
    }

    @Test
    @DisplayName("Criação com início no passado mostra a mensagem do Bean Validation da API")
    void criacaoNoPassadoMostraMensagemDeValidacao() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/votacoes")
                                .with(logado(admin))
                                .with(csrf())
                                .param("nome", "Conselho")
                                .param("descricao", "Eleição do conselho")
                                .param("tipo", "ELEICAO_PRIVADA")
                                .param("inicioEm", "2000-01-01T09:00")
                                .param("fimEm", "2099-10-01T09:00"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "A data de início não pode estar no passado")))
                .andExpect(content().string(containsString("value=\"Conselho\"")));
        assertThat(votacaoRepository.count()).isZero();
    }

    @Test
    @DisplayName("Regras válidas são salvas e redirecionam para os candidatos")
    void regrasValidasRedirecionam() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act
        mvc.perform(
                        post("/votacoes/{id}/regras", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("tipoMaioria", "MAIORIA_QUALIFICADA")
                                .param("percentualQualificado", "66.67")
                                .param("quorumMinimoPercentual", "")
                                .param("segundoTurno", "true"))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/candidatos"))
                .andExpect(flash().attribute("sucesso", "Regras salvas."));

        // Assert
        Votacao salva = votacaoRepository.findById(votacao.getId()).orElseThrow();
        assertThat(salva.getRegras().tipoMaioria()).isEqualTo(TipoMaioria.MAIORIA_QUALIFICADA);
        assertThat(salva.getRegras().segundoTurno()).isTrue();
        assertThat(salva.getRegras().votoPonderado()).isFalse();
    }

    @Test
    @DisplayName("Combinação de regras inválida mostra a mensagem do value object")
    void regrasInvalidasMostramMensagemDoDominio() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/regras", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("tipoMaioria", "MAIORIA_SIMPLES")
                                .param("segundoTurno", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/regras"))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "O segundo turno exige maioria absoluta ou"
                                                        + " qualificada")));
    }

    @Test
    @DisplayName("Regras sem tipo de maioria mostram a mensagem do Bean Validation")
    void regrasSemMaioriaMostramErroDeCampo() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/regras", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("tipoMaioria", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("O tipo de maioria é obrigatório")));
    }

    @Test
    @DisplayName("Cadastro de candidato redireciona e aparece na lista")
    void cadastroDeCandidato() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act
        mvc.perform(
                        post("/votacoes/{id}/candidatos", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("nome", "Chapa Azul")
                                .param("descricao", "Renovação"))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/candidatos"));

        // Assert
        mvc.perform(get("/votacoes/{id}/candidatos", votacao.getId()).with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chapa Azul")));
    }

    @Test
    @DisplayName("Candidato com nome repetido mostra a mensagem do domínio")
    void candidatoRepetidoMostraMensagem() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        candidatoService.cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa A", null));

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/candidatos", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("nome", "chapa a"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/candidatos"))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Já existe candidato/chapa com o nome &#39;chapa"
                                                        + " a&#39; nesta votação")));
        assertThat(candidatoRepository.countByVotacaoId(votacao.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Remoção de candidato redireciona com confirmação")
    void remocaoDeCandidato() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        UUID candidatoId =
                candidatoService
                        .cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa A", null))
                        .getId();

        // Act
        mvc.perform(
                        post("/votacoes/{id}/candidatos/{c}/remover", votacao.getId(), candidatoId)
                                .with(logado(admin))
                                .with(csrf()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/candidatos"))
                .andExpect(flash().attribute("sucesso", "Candidato/chapa removido."));

        // Assert
        assertThat(candidatoRepository.countByVotacaoId(votacao.getId())).isZero();
    }

    @Test
    @DisplayName("Eleitor cadastrado é adicionado e aparece na tabela")
    void adicionaEleitor() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        usuarioRepository.save(
                new Usuario("Bia", "52998224725", "bia@example.com", "hash", NASCIMENTO));

        // Act
        mvc.perform(
                        post("/votacoes/{id}/eleitores", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("email", "bia@example.com")
                                .param("peso", ""))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId() + "/eleitores"));

        // Assert
        mvc.perform(get("/votacoes/{id}/eleitores", votacao.getId()).with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bia@example.com")));
    }

    @Test
    @DisplayName("Eleitor sem cadastro mostra a mensagem do domínio")
    void eleitorInexistenteMostraMensagem() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/eleitores", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("email", "ninguem@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("votacoes/eleitores"))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Nenhum usuário cadastrado com o e-mail"
                                                        + " &#39;ninguem@example.com&#39;")));
    }

    @Test
    @DisplayName("Peso diferente de 1 sem voto ponderado mostra a mensagem do domínio")
    void pesoSemVotoPonderadoMostraMensagem() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        usuarioRepository.save(
                new Usuario("Bia", "52998224725", "bia@example.com", "hash", NASCIMENTO));

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/eleitores", votacao.getId())
                                .with(logado(admin))
                                .with(csrf())
                                .param("email", "bia@example.com")
                                .param("peso", "2"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "Peso diferente de 1 só é permitido em votação"
                                                        + " com voto ponderado")));
    }

    @Test
    @DisplayName("Publicação sem regras volta ao painel com a mensagem da guarda")
    void publicacaoRecusadaMostraMensagem() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        candidatoService.cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa A", null));
        candidatoService.cadastrar(votacao.getId(), new CadastrarCandidatoRequest("Chapa B", null));

        // Act & Assert
        mvc.perform(
                        post("/votacoes/{id}/publicar", votacao.getId())
                                .with(logado(admin))
                                .with(csrf()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId()))
                .andExpect(
                        flash().attribute(
                                        "erro",
                                        containsString(
                                                "as regras de apuração ainda não foram"
                                                        + " configuradas")));
        assertThat(votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.RascunhoVotacao);
    }

    @Test
    @DisplayName("Painel exibe a mensagem de erro recebida por flash")
    void painelExibeErroDoFlash() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act & Assert
        mvc.perform(
                        get("/votacoes/{id}", votacao.getId())
                                .with(logado(admin))
                                .flashAttr("erro", "Mensagem do domínio"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mensagem do domínio")))
                .andExpect(content().string(containsString("Publicar votação")));
    }

    @Test
    @DisplayName("Votação completa é publicada pelo VotacaoStateService e trava as etapas")
    void publicacaoValida() throws Exception {
        // Arrange
        Votacao votacao = prontaParaPublicar();

        // Act
        mvc.perform(
                        post("/votacoes/{id}/publicar", votacao.getId())
                                .with(logado(admin))
                                .with(csrf()))
                .andExpect(redirectedUrl("/votacoes/" + votacao.getId()))
                .andExpect(flash().attribute("sucesso", "Votação publicada."));

        // Assert
        assertThat(votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Publicada);
        mvc.perform(get("/votacoes/{id}/candidatos", votacao.getId()).with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não está mais em rascunho")))
                .andExpect(content().string(not(containsString("name=\"nome\""))));
    }

    @Test
    @DisplayName("Quem não é admin da votação recebe 403 nas telas dela")
    void naoAdminRecebe403() throws Exception {
        // Arrange
        Votacao votacao = rascunho();
        Usuario outro =
                usuarioRepository.save(
                        new Usuario("Bia", "52998224725", "bia@example.com", "hash", NASCIMENTO));

        // Act & Assert
        mvc.perform(get("/votacoes/{id}", votacao.getId()).with(logado(outro)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/votacoes/{id}/publicar", votacao.getId())
                                .with(logado(outro))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(votacaoRepository.findById(votacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.RascunhoVotacao);
    }

    @Test
    @DisplayName("Envio sem token CSRF é recusado")
    void semCsrfRecusado() throws Exception {
        // Arrange
        Votacao votacao = rascunho();

        // Act & Assert
        mvc.perform(post("/votacoes/{id}/publicar", votacao.getId()).with(logado(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Resumo mostra as contagens de candidatos e eleitores")
    void painelMostraContagens() throws Exception {
        // Arrange
        Votacao votacao = prontaParaPublicar();

        // Act & Assert
        mvc.perform(get("/votacoes/{id}", votacao.getId()).with(logado(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<dd>2</dd>")))
                .andExpect(content().string(containsString("Maioria simples")));
    }
}
