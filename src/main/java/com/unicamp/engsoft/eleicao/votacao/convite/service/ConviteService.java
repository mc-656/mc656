package com.unicamp.engsoft.eleicao.votacao.convite.service;

import com.unicamp.engsoft.eleicao.shared.exception.AcessoNegadoException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.convite.domain.ConviteVotacao;
import com.unicamp.engsoft.eleicao.votacao.convite.domain.ModoConvite;
import com.unicamp.engsoft.eleicao.votacao.convite.dto.ConviteAceitoResponse;
import com.unicamp.engsoft.eleicao.votacao.convite.dto.ConviteResponse;
import com.unicamp.engsoft.eleicao.votacao.convite.repository.ConviteVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.service.EleitorService;
import com.unicamp.engsoft.eleicao.votacao.service.ExigenciaDeEstadoDaVotacao;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convite de eleitores por link, e-mail ou QR Code (RF-17, RF-02).
 *
 * <p>O estado da votação manda em tudo, via {@link ExigenciaDeEstadoDaVotacao}: gerar, alterar e
 * enviar exigem {@link OperacaoVotacao#CONVIDAR_ELEITORES}; entrar pelo link exige {@link
 * OperacaoVotacao#ENTRAR_POR_CONVITE} — ambas só em {@code Publicada} e {@code EmVotacao}. Fora
 * deles o link continua existindo (o organizador ainda pode consultá-lo), mas não inclui ninguém.
 *
 * <p>A inclusão em si é do {@link EleitorService}: o convite só decide <em>se</em> o usuário pode
 * entrar (modo e lista), e quem grava a linha {@code ELEITOR} é o mesmo service da inclusão manual
 * (#63). A autorização de administrador fica no controller, via {@code @PreAuthorize}.
 */
@Service
public class ConviteService {

    private final ConviteVotacaoRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final VotacaoRepository votacaoRepository;
    private final ExigenciaDeEstadoDaVotacao exigenciaDeEstado;
    private final EleitorService eleitorService;
    private final GeradorQrCode geradorQrCode;
    private final EnvioConvitePorEmail envioPorEmail;
    private final Clock relogio;
    private final String urlBase;

    public ConviteService(
            ConviteVotacaoRepository conviteRepository,
            UsuarioRepository usuarioRepository,
            VotacaoRepository votacaoRepository,
            ExigenciaDeEstadoDaVotacao exigenciaDeEstado,
            EleitorService eleitorService,
            GeradorQrCode geradorQrCode,
            EnvioConvitePorEmail envioPorEmail,
            Clock relogio,
            @Value("${convite.url-base}") String urlBase) {
        this.conviteRepository = conviteRepository;
        this.usuarioRepository = usuarioRepository;
        this.votacaoRepository = votacaoRepository;
        this.exigenciaDeEstado = exigenciaDeEstado;
        this.eleitorService = eleitorService;
        this.geradorQrCode = geradorQrCode;
        this.envioPorEmail = envioPorEmail;
        this.relogio = relogio;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * Gera o convite, ou só troca o modo se ele já existir. O token é mantido de propósito: o link
     * que já circulou continua valendo, agora com a nova regra de acesso.
     */
    @Transactional
    public ConviteResponse gerar(UUID votacaoId, ModoConvite modo) {
        exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.CONVIDAR_ELEITORES);
        ConviteVotacao convite =
                conviteRepository
                        .findById(votacaoId)
                        .orElseGet(() -> new ConviteVotacao(votacaoId, modo, Instant.now(relogio)));
        convite.alterarModo(modo);
        return resposta(conviteRepository.save(convite));
    }

    @Transactional(readOnly = true)
    public ConviteResponse consultar(UUID votacaoId) {
        return resposta(buscar(votacaoId));
    }

    @Transactional(readOnly = true)
    public byte[] qrCode(UUID votacaoId) {
        return geradorQrCode.png(link(buscar(votacaoId)));
    }

    /**
     * Adiciona os endereços à lista do convite e envia uma mensagem a cada um. Os e-mails entram na
     * lista mesmo no modo aberto, para que o organizador possa restringir depois sem reenviar.
     *
     * <p>O envio acontece dentro da transação: se o SMTP falhar, a lista volta ao que era e o
     * organizador recebe {@code 502} para tentar de novo, em vez de uma lista que diz "convidado" a
     * quem nunca recebeu nada.
     */
    @Transactional
    public ConviteResponse enviarPorEmail(UUID votacaoId, List<String> emails) {
        Votacao votacao = exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.CONVIDAR_ELEITORES);
        ConviteVotacao convite = buscar(votacaoId);
        Set<String> destinatarios = new LinkedHashSet<>();
        emails.stream().map(ConviteVotacao::normalizar).forEach(destinatarios::add);
        convite.adicionarEmails(destinatarios);
        conviteRepository.save(convite);
        envioPorEmail.enviar(destinatarios, votacao.getNome(), link(convite));
        return resposta(convite);
    }

    /**
     * Inclui o usuário como eleitor pelo token do link.
     *
     * @throws RecursoNaoEncontradoException (404) se o token não existir.
     * @throws AcessoNegadoException (403) se o convite for restrito e o e-mail do usuário não
     *     estiver na lista — ele não é incluído.
     * @throws com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException (409) se a
     *     votação estiver fora de {@code Publicada}/{@code EmVotacao}.
     */
    @Transactional
    public ConviteAceitoResponse aceitar(String token, UUID usuarioId) {
        ConviteVotacao convite =
                conviteRepository
                        .findByToken(token)
                        .orElseThrow(
                                () ->
                                        new RecursoNaoEncontradoException(
                                                "Convite não encontrado ou link inválido"));
        Usuario usuario =
                usuarioRepository
                        .findById(usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        if (!convite.admite(usuario.getEmail())) {
            throw new AcessoNegadoException(
                    "Este convite é restrito e o seu e-mail não está na lista de convidados");
        }
        UUID votacaoId = convite.getVotacaoId();
        boolean incluido = eleitorService.incluirPorConvite(votacaoId, usuarioId);
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        return new ConviteAceitoResponse(votacaoId, votacao.getNome(), !incluido);
    }

    private ConviteVotacao buscar(UUID votacaoId) {
        return conviteRepository
                .findById(votacaoId)
                .orElseThrow(
                        () ->
                                new RecursoNaoEncontradoException(
                                        "A votação %s ainda não tem convite gerado"
                                                .formatted(votacaoId)));
    }

    private String link(ConviteVotacao convite) {
        return urlBase + "/convites/" + convite.getToken();
    }

    private ConviteResponse resposta(ConviteVotacao convite) {
        return ConviteResponse.of(convite, link(convite));
    }
}
