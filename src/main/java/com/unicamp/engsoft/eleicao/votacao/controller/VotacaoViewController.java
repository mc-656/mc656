package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.convite.domain.ModoConvite;
import com.unicamp.engsoft.eleicao.votacao.convite.dto.EnviarConvitesRequest;
import com.unicamp.engsoft.eleicao.votacao.convite.service.ConviteService;
import com.unicamp.engsoft.eleicao.votacao.domain.Apuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.AdicionarEleitorRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ComparecimentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoForm;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.DefinirOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.DefinirPeriodoForm;
import com.unicamp.engsoft.eleicao.votacao.dto.DefinirPeriodoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostasResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.AnulacaoVotacaoService;
import com.unicamp.engsoft.eleicao.votacao.service.AutorizacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.ComparecimentoService;
import com.unicamp.engsoft.eleicao.votacao.service.EleitorService;
import com.unicamp.engsoft.eleicao.votacao.service.PropostaService;
import com.unicamp.engsoft.eleicao.votacao.service.ResultadoVotacaoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoStateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
    private static final String ADMIN_OU_ELEITOR =
            "@autorizacaoVotacao.ehAdmin(#votacaoId, authentication) or"
                    + " @autorizacaoVotacao.ehEleitor(#votacaoId, authentication)";

    private static final String NOVA = "votacoes/nova";
    private static final String REGRAS = "votacoes/regras";
    private static final String CANDIDATOS = "votacoes/candidatos";
    private static final String PROPOSTAS = "votacoes/propostas";
    private static final String ELEITORES = "votacoes/eleitores";
    private static final String PAINEL = "votacoes/painel";

    private final VotacaoService votacaoService;
    private final CandidatoOuChapaService candidatoService;
    private final PropostaService propostaService;
    private final EleitorService eleitorService;
    private final VotacaoStateService stateService;
    private final ResultadoVotacaoService resultadoService;
    private final AnulacaoVotacaoService anulacaoService;
    private final ApuracaoRepository apuracaoRepository;
    private final AutorizacaoVotacao autorizacaoVotacao;
    private final ComparecimentoService comparecimentoService;
    private final ConviteService conviteService;
    private final Validator validator;

    public VotacaoViewController(
            VotacaoService votacaoService,
            CandidatoOuChapaService candidatoService,
            PropostaService propostaService,
            EleitorService eleitorService,
            VotacaoStateService stateService,
            ResultadoVotacaoService resultadoService,
            AnulacaoVotacaoService anulacaoService,
            ApuracaoRepository apuracaoRepository,
            AutorizacaoVotacao autorizacaoVotacao,
            ComparecimentoService comparecimentoService,
            ConviteService conviteService,
            Validator validator) {
        this.votacaoService = votacaoService;
        this.candidatoService = candidatoService;
        this.propostaService = propostaService;
        this.eleitorService = eleitorService;
        this.stateService = stateService;
        this.resultadoService = resultadoService;
        this.anulacaoService = anulacaoService;
        this.apuracaoRepository = apuracaoRepository;
        this.autorizacaoVotacao = autorizacaoVotacao;
        this.comparecimentoService = comparecimentoService;
        this.conviteService = conviteService;
        this.validator = validator;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("votacoes", votacaoService.listarAdministradasPor(usuario.getId()));
        model.addAttribute("votacoesEleitor", votacaoService.listarOndeEhEleitor(usuario.getId()));
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
        validarRequest(request, erros);
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
        model.addAttribute("periodoForm", periodoInicial(votacao));
        return prepararPainel(votacao, model);
    }

    @PostMapping("/{votacaoId}/periodo")
    @PreAuthorize(ADMIN)
    public String definirPeriodo(
            @PathVariable UUID votacaoId,
            @ModelAttribute("periodoForm") DefinirPeriodoForm form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            DefinirPeriodoRequest request = form.paraRequest(RotulosVotacao.FUSO);
            validarRequest(request, erros);
            if (!erros.hasErrors()) {
                try {
                    votacaoService.definirPeriodo(votacaoId, request.inicioEm(), request.fimEm());
                    redirect.addFlashAttribute("sucesso", "Período da votação salvo.");
                    return "redirect:/votacoes/" + votacaoId;
                } catch (DomainException ex) {
                    erros.reject("periodo.recusado", ex.getMessage());
                }
            }
        }
        return prepararPainel(carregar(votacaoId, model), model);
    }

    @GetMapping(value = "/{votacaoId}/comparecimento", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("@autorizacaoVotacao.ehParticipante(#votacaoId, authentication)")
    public ComparecimentoResponse comparecimentoJson(@PathVariable UUID votacaoId) {
        return comparecimentoService.consultar(votacaoId);
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

    @PostMapping("/{votacaoId}/encerrar")
    @PreAuthorize(ADMIN)
    public String encerrar(
            @PathVariable UUID votacaoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () ->
                        stateService.transitar(
                                votacaoId, EstadoVotacao.Encerrada, usuario.getId(), null),
                "Resultado publicado.",
                "/votacoes/" + votacaoId + "/resultado",
                redirect);
    }

    @PostMapping("/{votacaoId}/homologar")
    @PreAuthorize(ADMIN)
    public String homologar(
            @PathVariable UUID votacaoId,
            @RequestParam(required = false) String motivo,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            RedirectAttributes redirect) {
        String motivoNormalizado = motivo == null || motivo.isBlank() ? null : motivo.strip();
        return executarERedirecionar(
                () ->
                        stateService.transitar(
                                votacaoId,
                                EstadoVotacao.Homologada,
                                usuario.getId(),
                                motivoNormalizado),
                "Resultado homologado.",
                "/votacoes/" + votacaoId + "/resultado",
                redirect);
    }

    @PostMapping("/{votacaoId}/anular")
    @PreAuthorize(ADMIN)
    public String anular(
            @PathVariable UUID votacaoId,
            @RequestParam(defaultValue = "") String justificativa,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () ->
                        anulacaoService.anularPorIrregularidade(
                                votacaoId, usuario.getId(), justificativa),
                "Votação anulada.",
                "/votacoes/" + votacaoId + "/resultado",
                redirect);
    }

    @GetMapping("/{votacaoId}/resultado")
    @PreAuthorize(ADMIN_OU_ELEITOR)
    public String resultado(
            @PathVariable UUID votacaoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Authentication autenticacao,
            Model model) {
        model.addAttribute("v", votacaoService.buscar(votacaoId));
        model.addAttribute(
                "resultado", resultadoService.consultarResultado(votacaoId, usuario.getId()));
        model.addAttribute("ehAdmin", autorizacaoVotacao.ehAdmin(votacaoId, autenticacao));
        return "votacoes/resultado";
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

    @GetMapping("/{votacaoId}/propostas")
    @PreAuthorize(ADMIN)
    public String propostas(@PathVariable UUID votacaoId, Model model) {
        return prepararPropostas(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/orcamento")
    @PreAuthorize(ADMIN)
    public String definirOrcamento(
            @PathVariable UUID votacaoId,
            @Valid @ModelAttribute("orcamentoForm") DefinirOrcamentoRequest request,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            try {
                propostaService.definirOrcamento(votacaoId, request);
                redirect.addFlashAttribute("sucesso", "Orçamento total salvo.");
                return "redirect:/votacoes/" + votacaoId + "/propostas";
            } catch (DomainException ex) {
                erros.reject("orcamento.recusado", ex.getMessage());
            }
        }
        return prepararPropostas(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/propostas")
    @PreAuthorize(ADMIN)
    public String cadastrarProposta(
            @PathVariable UUID votacaoId,
            @Valid @ModelAttribute("propostaForm") PropostaRequest request,
            BindingResult erros,
            Model model,
            RedirectAttributes redirect) {
        if (!erros.hasErrors()) {
            try {
                propostaService.cadastrar(votacaoId, request);
                redirect.addFlashAttribute("sucesso", "Proposta cadastrada.");
                return "redirect:/votacoes/" + votacaoId + "/propostas";
            } catch (DomainException ex) {
                erros.reject("proposta.recusada", ex.getMessage());
            }
        }
        return prepararPropostas(carregar(votacaoId, model), model);
    }

    @PostMapping("/{votacaoId}/propostas/{propostaId}/editar")
    @PreAuthorize(ADMIN)
    public String editarProposta(
            @PathVariable UUID votacaoId,
            @PathVariable UUID propostaId,
            @Valid @ModelAttribute PropostaRequest request,
            BindingResult erros,
            RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            redirect.addFlashAttribute("erro", "Dados inválidos para atualizar a proposta.");
            return "redirect:/votacoes/" + votacaoId + "/propostas";
        }
        return executarERedirecionar(
                () -> propostaService.editar(votacaoId, propostaId, request),
                "Proposta atualizada.",
                "/votacoes/" + votacaoId + "/propostas",
                redirect);
    }

    @PostMapping("/{votacaoId}/propostas/{propostaId}/remover")
    @PreAuthorize(ADMIN)
    public String removerProposta(
            @PathVariable UUID votacaoId,
            @PathVariable UUID propostaId,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () -> propostaService.remover(votacaoId, propostaId),
                "Proposta removida.",
                "/votacoes/" + votacaoId + "/propostas",
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

    private String prepararPropostas(Votacao votacao, Model model) {
        PropostasResponse info = propostaService.listar(votacao.getId());
        model.addAttribute("orcamentoTotal", info.orcamentoTotal());
        model.addAttribute("propostas", info.propostas());
        model.addAttribute("avisos", info.avisos());
        if (!model.containsAttribute("orcamentoForm")) {
            model.addAttribute("orcamentoForm", new DefinirOrcamentoRequest(info.orcamentoTotal()));
        }
        if (!model.containsAttribute("propostaForm")) {
            model.addAttribute("propostaForm", new PropostaRequest(null, null, null));
        }
        model.addAttribute(
                "editavel",
                votacao.getTipo() == TipoVotacao.ORCAMENTO_PARTICIPATIVO
                        && ComportamentosDeEstado.de(votacao.getEstado())
                                .permite(OperacaoVotacao.GERENCIAR_PROPOSTAS));
        return PROPOSTAS;
    }

    private String prepararEleitores(Votacao votacao, Model model) {
        model.addAttribute("eleitores", eleitorService.listar(votacao.getId()));
        model.addAttribute("avisosPesoUnico", eleitorService.avisosPesoUnico(votacao.getId()));
        model.addAttribute(
                "votoPonderado",
                votacao.getRegras() != null && votacao.getRegras().votoPonderado());
        editavel(votacao, OperacaoVotacao.GERENCIAR_ELEITORES, model);
        return ELEITORES;
    }

    private String prepararPainel(Votacao votacao, Model model) {
        UUID votacaoId = votacao.getId();
        Apuracao apuracao = apuracaoRepository.findById(votacaoId).orElse(null);
        EstadoVotacao estado = votacao.getEstado();
        model.addAttribute("totalCandidatos", candidatoService.contar(votacaoId));
        model.addAttribute(
                "totalPropostas",
                votacao.getTipo() == TipoVotacao.ORCAMENTO_PARTICIPATIVO
                        ? propostaService.listar(votacaoId).propostas().size()
                        : 0);
        model.addAttribute("totalEleitores", eleitorService.contar(votacaoId));
        model.addAttribute("podePublicar", estado == EstadoVotacao.RascunhoVotacao);
        model.addAttribute("avisosPesoUnico", eleitorService.avisosPesoUnico(votacaoId));
        if (estado == EstadoVotacao.EmVotacao || estado == EstadoVotacao.Suspensa) {
            model.addAttribute("comparecimento", comparecimentoService.consultar(votacaoId));
        }
        model.addAttribute(
                "editavelPeriodo",
                ComportamentosDeEstado.de(estado).permite(OperacaoVotacao.CONFIGURAR_PERIODO));
        model.addAttribute("apuracao", apuracao);
        model.addAttribute(
                "podeEncerrar",
                estado == EstadoVotacao.Apuracao && apuracao != null && apuracao.quorumAtingido());
        model.addAttribute("podeAnular", estado == EstadoVotacao.Apuracao);
        model.addAttribute("podeHomologar", estado == EstadoVotacao.Encerrada);
        model.addAttribute(
                "temResultadoOuAnulacao",
                estado == EstadoVotacao.Encerrada
                        || estado == EstadoVotacao.Homologada
                        || estado == EstadoVotacao.Anulada
                        || (estado == EstadoVotacao.Apuracao && apuracao != null));
        boolean podeConvidar =
                ComportamentosDeEstado.de(estado).permite(OperacaoVotacao.CONVIDAR_ELEITORES);
        model.addAttribute("podeConvidar", podeConvidar);
        if (podeConvidar) {
            try {
                model.addAttribute("convite", conviteService.consultar(votacaoId));
            } catch (RecursoNaoEncontradoException ignored) {
                // Convite ainda não foi gerado; a tela exibe apenas o formulário de criação.
            }
            model.addAttribute("modosConvite", ModoConvite.values());
        }
        return PAINEL;
    }

    @PostMapping("/{votacaoId}/convite")
    @PreAuthorize(ADMIN)
    public String gerarConvite(
            @PathVariable UUID votacaoId,
            @RequestParam(defaultValue = "ABERTO") ModoConvite modo,
            RedirectAttributes redirect) {
        return executarERedirecionar(
                () -> conviteService.gerar(votacaoId, modo),
                "Convite salvo.",
                "/votacoes/" + votacaoId,
                redirect);
    }

    @PostMapping("/{votacaoId}/convite/emails")
    @PreAuthorize(ADMIN)
    public String enviarConvitesPorEmail(
            @PathVariable UUID votacaoId,
            @RequestParam(defaultValue = "") String emails,
            RedirectAttributes redirect) {
        List<String> lista =
                Arrays.stream(emails.split("[,;\\r\\n]+"))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .toList();
        Set<ConstraintViolation<EnviarConvitesRequest>> violacoes =
                validator.validate(new EnviarConvitesRequest(lista));
        if (!violacoes.isEmpty()) {
            redirect.addFlashAttribute("erro", violacoes.iterator().next().getMessage());
            return "redirect:/votacoes/" + votacaoId;
        }
        return executarERedirecionar(
                () -> conviteService.enviarPorEmail(votacaoId, lista),
                "Convites enviados por e-mail.",
                "/votacoes/" + votacaoId,
                redirect);
    }

    private static DefinirPeriodoForm periodoInicial(Votacao votacao) {
        if (votacao.getInicioEm() == null || votacao.getFimEm() == null) {
            return DefinirPeriodoForm.vazio();
        }
        return new DefinirPeriodoForm(
                LocalDateTime.ofInstant(votacao.getInicioEm(), RotulosVotacao.FUSO),
                LocalDateTime.ofInstant(votacao.getFimEm(), RotulosVotacao.FUSO));
    }

    private <T> void validarRequest(T request, BindingResult erros) {
        for (ConstraintViolation<T> violacao : validator.validate(request)) {
            erros.rejectValue(
                    violacao.getPropertyPath().toString(), "invalido", violacao.getMessage());
        }
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
