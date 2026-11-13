package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.AdicionarEleitorRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoForm;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.EleitorService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas do administrador para os fluxos do M1 (#67): criar votação (RF-10), configurar regras
 * (RF-11), cadastrar candidatos/chapas (RF-12), gerir eleitores (RF-02/RF-10) e publicar (SPECS
 * §4.2).
 *
 * <p>É uma casca sobre os mesmos services e DTOs da API: nenhuma regra de negócio mora aqui. Erro
 * de domínio ({@link DomainException}) vira erro global do formulário com a mensagem do próprio
 * domínio, como no cadastro de usuário; a publicação passa pelo {@link VotacaoStateService}, único
 * ponto de transição (SPECS §7.3). A autorização é a mesma da API, via {@code @PreAuthorize} e o
 * bean {@code autorizacaoVotacao}, que também entende o principal da sessão.
 *
 * <p>Sucesso sempre redireciona (PRG), para o F5 não reenviar o formulário; a mensagem de
 * confirmação vai por flash attribute.
 */
@Controller
@RequestMapping("/votacoes")
public class VotacaoViewController {

    private static final String ADMIN = "@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)";

    private static final String NOVA = "votacoes/nova";
    private static final String REGRAS = "votacoes/regras";
    private static final String CANDIDATOS = "votacoes/candidatos";
    private static final String ELEITORES = "votacoes/eleitores";

    private final VotacaoService votacaoService;
    private final CandidatoOuChapaService candidatoService;
    private final EleitorService eleitorService;
    private final VotacaoStateService stateService;
    private final Validator validator;

    public VotacaoViewController(
            VotacaoService votacaoService,
            CandidatoOuChapaService candidatoService,
            EleitorService eleitorService,
            VotacaoStateService stateService,
            Validator validator) {
        this.votacaoService = votacaoService;
        this.candidatoService = candidatoService;
        this.eleitorService = eleitorService;
        this.stateService = stateService;
        this.validator = validator;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("votacoes", votacaoService.listarAdministradasPor(usuario.getId()));
        return "votacoes/lista";
    }

    @GetMapping("/nova")
    public String formularioCriacao(Model model) {
        model.addAttribute("votacao", CriarVotacaoForm.vazio());
        model.addAttribute("tipos", TipoVotacao.values());
        return NOVA;
    }

    /**
     * Valida o {@link CriarVotacaoRequest} (e não o formulário) para reaproveitar as anotações e
     * mensagens da API. Só valida se o binding passou: com data malformada o campo chega nulo e o
     * {@code @NotNull} repetiria um erro que o binding já apontou.
     */
    @PostMapping
    public String criar(
            @ModelAttribute("votacao") CriarVotacaoForm form,
            BindingResult erros,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model,
            RedirectAttributes redirect) {
        model.addAttribute("tipos", TipoVotacao.values());
        if (erros.hasErrors()) {
            return NOVA;
        }
        CriarVotacaoRequest request = form.paraRequest(RotulosVotacao.FUSO);
        for (ConstraintViolation<CriarVotacaoRequest> violacao : validator.validate(request)) {
            erros.rejectValue(
                    violacao.getPropertyPath().toString(), "invalido", violacao.getMessage());
        }
        if (erros.hasErrors()) {
            return NOVA;
        }
        Votacao votacao;
        try {
            votacao = votacaoService.criarVotacao(request, usuario.getId());
        } catch (DomainException ex) {
            erros.reject("votacao.recusada", ex.getMessage());
            return NOVA;
        }
        redirect.addFlashAttribute("sucesso", "Votação criada. Agora configure as regras.");
        return "redirect:/votacoes/" + votacao.getId() + "/regras";
    }

    @GetMapping("/{votacaoId}")
    @PreAuthorize(ADMIN)
    public String painel(@PathVariable UUID votacaoId, Model model) {
        Votacao votacao = carregar(votacaoId, model);
        model.addAttribute("totalCandidatos", candidatoService.contar(votacaoId));
        model.addAttribute("totalEleitores", eleitorService.contar(votacaoId));
        model.addAttribute("podePublicar", votacao.getEstado() == EstadoVotacao.RascunhoVotacao);
        return "votacoes/painel";
    }

    /**
     * O botão só aparece em rascunho (atalho de tela: a tabela de transições não é pública), mas
     * quem decide se a publicação vale (estado e guardas de publicação) é o {@link
     * VotacaoStateService}; a recusa volta ao painel com a mensagem dele.
     */
    @PostMapping("/{votacaoId}/publicar")
    @PreAuthorize(ADMIN)
    public String publicar(
            @PathVariable UUID votacaoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () ->
                        stateService.transitar(
                                votacaoId, EstadoVotacao.Publicada, usuario.getId(), null),
                "Votação publicada.",
                "/votacoes/" + votacaoId,
                redirect);
    }

    @GetMapping("/{votacaoId}/regras")
    @PreAuthorize(ADMIN)
    public String formularioRegras(@PathVariable UUID votacaoId, Model model) {
        Votacao votacao = carregar(votacaoId, model);
        model.addAttribute("regras", paraFormulario(votacao.getRegras()));
        return prepararRegras(votacao, model);
    }

    @PostMapping("/{votacaoId}/regras")
    @PreAuthorize(ADMIN)
    public String configurarRegras(
            @PathVariable UUID votacaoId,
            @Valid @ModelAttribute("regras") ConfigurarRegrasRequest request,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            try {
                votacaoService.configurarRegras(votacaoId, request);
                redirect.addFlashAttribute("sucesso", "Regras salvas.");
                return "redirect:/votacoes/" + votacaoId + "/candidatos";
            } catch (DomainException ex) {
                erros.reject("regras.recusadas", ex.getMessage());
            }
        }
        return prepararRegras(carregar(votacaoId, model), model);
    }

    @GetMapping("/{votacaoId}/candidatos")
    @PreAuthorize(ADMIN)
    public String candidatos(@PathVariable UUID votacaoId, Model model) {
        model.addAttribute("candidato", new CadastrarCandidatoRequest(null, null));
        return prepararCandidatos(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/candidatos")
    @PreAuthorize(ADMIN)
    public String cadastrarCandidato(
            @PathVariable UUID votacaoId,
            @Valid @ModelAttribute("candidato") CadastrarCandidatoRequest request,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            try {
                candidatoService.cadastrar(votacaoId, request);
                redirect.addFlashAttribute("sucesso", "Candidato/chapa cadastrado.");
                return "redirect:/votacoes/" + votacaoId + "/candidatos";
            } catch (DomainException ex) {
                erros.reject("candidato.recusado", ex.getMessage());
            }
        }
        return prepararCandidatos(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/candidatos/{candidatoId}/remover")
    @PreAuthorize(ADMIN)
    public String removerCandidato(
            @PathVariable UUID votacaoId,
            @PathVariable UUID candidatoId,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () -> candidatoService.remover(votacaoId, candidatoId),
                "Candidato/chapa removido.",
                "/votacoes/" + votacaoId + "/candidatos",
                redirect);
    }

    @GetMapping("/{votacaoId}/eleitores")
    @PreAuthorize(ADMIN)
    public String eleitores(@PathVariable UUID votacaoId, Model model) {
        model.addAttribute("eleitor", new AdicionarEleitorRequest(null, null));
        return prepararEleitores(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/eleitores")
    @PreAuthorize(ADMIN)
    public String adicionarEleitor(
            @PathVariable UUID votacaoId,
            @Valid @ModelAttribute("eleitor") AdicionarEleitorRequest request,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            try {
                eleitorService.adicionar(votacaoId, request);
                redirect.addFlashAttribute("sucesso", "Eleitor adicionado.");
                return "redirect:/votacoes/" + votacaoId + "/eleitores";
            } catch (DomainException ex) {
                erros.reject("eleitor.recusado", ex.getMessage());
            }
        }
        return prepararEleitores(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/eleitores/{usuarioId}/remover")
    @PreAuthorize(ADMIN)
    public String removerEleitor(
            @PathVariable UUID votacaoId,
            @PathVariable UUID usuarioId,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () -> eleitorService.remover(votacaoId, usuarioId),
                "Eleitor removido.",
                "/votacoes/" + votacaoId + "/eleitores",
                redirect);
    }

    /**
     * Ações sem formulário próprio (publicar, remover): o resultado, de sucesso ou a mensagem do
     * domínio, volta por flash para a página de origem.
     */
    private static String executarERedirecionar(
            Runnable acao, String sucesso, String destino, RedirectAttributes redirect) {
        try {
            acao.run();
            redirect.addFlashAttribute("sucesso", sucesso);
        } catch (DomainException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:" + destino;
    }

    /**
     * Erro de domínio fora de um formulário (votação removida entre a autorização e a leitura, por
     * exemplo). O {@code GlobalExceptionHandler} só atende {@code @RestController}; sem este
     * handler a tela receberia 500. Responde com o status do próprio domínio na página de erro.
     */
    @ExceptionHandler(DomainException.class)
    public String erroDeDominio(DomainException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getStatus().value());
        model.addAttribute("status", ex.getStatus().value());
        return "error";
    }

    private Votacao carregar(UUID votacaoId, Model model) {
        Votacao votacao = votacaoService.buscar(votacaoId);
        model.addAttribute("v", votacao);
        return votacao;
    }

    /**
     * {@code editavel} só esconde formulários que o estado atual recusaria; pergunta ao próprio
     * comportamento do estado em vez de repetir aqui a regra "só em rascunho".
     */
    private static void editavel(Votacao votacao, OperacaoVotacao operacao, Model model) {
        model.addAttribute(
                "editavel", ComportamentosDeEstado.de(votacao.getEstado()).permite(operacao));
    }

    private String prepararRegras(Votacao votacao, Model model) {
        model.addAttribute("maiorias", TipoMaioria.values());
        editavel(votacao, OperacaoVotacao.CONFIGURAR_REGRAS, model);
        return REGRAS;
    }

    private String prepararCandidatos(Votacao votacao, Model model) {
        model.addAttribute("candidatos", candidatoService.listar(votacao.getId()));
        editavel(votacao, OperacaoVotacao.GERENCIAR_CANDIDATOS, model);
        return CANDIDATOS;
    }

    private String prepararEleitores(Votacao votacao, Model model) {
        model.addAttribute("eleitores", eleitorService.listar(votacao.getId()));
        model.addAttribute(
                "votoPonderado",
                votacao.getRegras() != null && votacao.getRegras().votoPonderado());
        editavel(votacao, OperacaoVotacao.GERENCIAR_ELEITORES, model);
        return ELEITORES;
    }

    private static ConfigurarRegrasRequest paraFormulario(RegraVotacao regra) {
        if (regra == null) {
            return new ConfigurarRegrasRequest(null, null, false, null, false);
        }
        return new ConfigurarRegrasRequest(
                regra.tipoMaioria(),
                regra.percentualQualificado(),
                regra.segundoTurno(),
                regra.quorumMinimoPercentual(),
                regra.votoPonderado());
    }
}
