package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificacaoComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.CandidatoOuChapaService;
import com.unicamp.engsoft.eleicao.votacao.service.RegistroVotoService;
import com.unicamp.engsoft.eleicao.votacao.service.VotacaoService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas Web do Eleitor: cédula de votação (RF-13), bloqueio de voto duplicado ou fora da janela
 * (RF-14), exibição do comprovante criptográfico e verificação de autenticidade (RNF-01).
 *
 * <p>Assim como no {@link VotacaoViewController}, esta classe atua como casca fina sobre os
 * serviços de domínio ({@link RegistroVotoService}, {@link VotacaoService}, {@link
 * CandidatoOuChapaService}). A autorização segue as regras do sistema via {@code @PreAuthorize} e o
 * bean {@code autorizacaoVotacao}.
 *
 * <p>O envio do voto segue o padrão Post-Redirect-Get (PRG): a submissão redireciona para a página
 * do comprovante com o código criptográfico enviado via flash attribute, evitando reenvio acidental
 * em atualizações de página (F5).
 */
@Controller
public class VotoViewController {

    private static final String ELEITOR =
            "@autorizacaoVotacao.ehEleitor(#votacaoId, authentication)";

    private final VotacaoService votacaoService;
    private final CandidatoOuChapaService candidatoService;
    private final RegistroVotoService registroVotoService;
    private final ParticipacaoRepository participacaoRepository;
    private final Clock clock;

    public VotoViewController(
            VotacaoService votacaoService,
            CandidatoOuChapaService candidatoService,
            RegistroVotoService registroVotoService,
            ParticipacaoRepository participacaoRepository,
            Clock clock) {
        this.votacaoService = votacaoService;
        this.candidatoService = candidatoService;
        this.registroVotoService = registroVotoService;
        this.participacaoRepository = participacaoRepository;
        this.clock = clock;
    }

    /** Exibe a cédula de votação para o eleitor. */
    @GetMapping("/votacoes/{votacaoId}/votar")
    @PreAuthorize(ELEITOR)
    public String votar(
            @PathVariable UUID votacaoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model) {
        Votacao votacao = votacaoService.buscar(votacaoId);
        boolean jaVotou =
                participacaoRepository.existsByUsuarioIdAndVotacaoId(usuario.getId(), votacaoId);

        Instant agora = clock.instant();
        boolean estadoPermite =
                ComportamentosDeEstado.de(votacao.getEstado()).permite(OperacaoVotacao.VOTAR);
        boolean aberta = estadoPermite && agora.isBefore(votacao.getFimEm());

        model.addAttribute("votacao", votacao);
        model.addAttribute("candidatos", candidatoService.listar(votacaoId));
        model.addAttribute("jaVotou", jaVotou);
        model.addAttribute("aberta", aberta);

        return "votacoes/votar";
    }

    /** Registra o voto único do eleitor e redireciona para o comprovante. */
    @PostMapping("/votacoes/{votacaoId}/votar")
    @PreAuthorize(ELEITOR)
    public String registrarVoto(
            @PathVariable UUID votacaoId,
            @RequestParam(name = "opcaoId", required = false) UUID opcaoId,
            Authentication autenticacao,
            RedirectAttributes redirect) {
        if (opcaoId == null) {
            redirect.addFlashAttribute("erro", "Selecione uma opção para votar.");
            return "redirect:/votacoes/" + votacaoId + "/votar";
        }

        try {
            ComprovanteResponse response =
                    registroVotoService.registrar(votacaoId, opcaoId, autenticacao);
            redirect.addFlashAttribute("comprovante", response.comprovante());
            redirect.addFlashAttribute("emitidoEm", response.emitidoEm());
            redirect.addFlashAttribute("sucesso", "Voto registrado com sucesso!");
            return "redirect:/votacoes/" + votacaoId + "/comprovante";
        } catch (DomainException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/votacoes/" + votacaoId + "/votar";
        }
    }

    /** Exibe o comprovante emitido após o voto. */
    @GetMapping("/votacoes/{votacaoId}/comprovante")
    @PreAuthorize(ELEITOR)
    public String comprovante(@PathVariable UUID votacaoId, Model model) {
        Votacao votacao = votacaoService.buscar(votacaoId);
        model.addAttribute("votacao", votacao);
        return "votacoes/comprovante";
    }

    /** Formulário para verificação de comprovante criptográfico (RNF-01). */
    @GetMapping("/comprovantes/verificar")
    public String formularioVerificacao(
            @RequestParam(name = "codigo", required = false) String codigo, Model model) {
        if (codigo != null && !codigo.isBlank()) {
            return verificarComprovante(codigo, model);
        }
        return "votacoes/verificar-comprovante";
    }

    /** Executa a verificação do comprovante criptográfico. */
    @PostMapping("/comprovantes/verificar")
    public String verificarComprovante(
            @RequestParam(name = "comprovante", required = false) String comprovante, Model model) {
        if (comprovante == null || comprovante.isBlank()) {
            model.addAttribute("erro", "Informe o código do comprovante.");
            return "votacoes/verificar-comprovante";
        }

        String limpo = comprovante.trim();
        model.addAttribute("comprovanteInformado", limpo);

        try {
            VerificacaoComprovanteResponse verificado = registroVotoService.verificar(limpo);
            Votacao votacao = votacaoService.buscar(verificado.votacaoId());
            model.addAttribute("verificado", verificado);
            model.addAttribute("votacao", votacao);
            model.addAttribute("comprovanteValido", true);
        } catch (DomainException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("comprovanteValido", false);
        }

        return "votacoes/verificar-comprovante";
    }

    @ExceptionHandler(DomainException.class)
    public String erroDeDominio(DomainException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getStatus().value());
        model.addAttribute("status", ex.getStatus().value());
        return "error";
    }
}
