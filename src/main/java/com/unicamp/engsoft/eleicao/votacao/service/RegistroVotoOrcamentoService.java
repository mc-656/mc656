package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.domain.AlocacaoOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.CedulaOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.Participacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoDuplicadoException;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoForaDaJanelaException;
import com.unicamp.engsoft.eleicao.votacao.dto.AlocacaoPontoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.SaldoOrcamentoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotoOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.AlocacaoOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de votos e validação de cotas do Orçamento Participativo (RF-21, RF-22, RNF-01, RNF-02).
 *
 * <p><b>Subestado ValidandoCotas.</b> Durante o estado {@code EmVotacao}, cada voto passa pela
 * validação rigorosa de cotas e saldo: a soma dos pontos alocados não pode exceder o saldo do
 * eleitor ({@code orcamentoTotal * peso}), nenhum ponto pode ser negativo e todas as propostas
 * devem pertencer à votação em andamento.
 *
 * <p><b>Sigilo e integridade (RNF-02).</b> A participação do eleitor ({@link Participacao}) é
 * gravada de forma isolada das alocações de pontos ({@link CedulaOrcamento} e {@link
 * AlocacaoOrcamento}), sem nenhuma chave estrangeira ou coluna que os correlacione no banco de
 * dados. Ambas as tabelas são append-only no PostgreSQL.
 */
@Service
public class RegistroVotoOrcamentoService {

    private static final String UNIQUE_ELEITOR = "participacoes_eleitor_unico";

    private final VotacaoRepository votacaoRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final PapelVotacaoRepository papelVotacaoRepository;
    private final PropostaRepository propostaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final CedulaOrcamentoRepository cedulaOrcamentoRepository;
    private final AlocacaoOrcamentoRepository alocacaoOrcamentoRepository;
    private final ComprovanteService comprovanteService;
    private final Clock clock;
    private final MetricasVotacao metricasVotacao;

    public RegistroVotoOrcamentoService(
            VotacaoRepository votacaoRepository,
            ElegibilidadeService elegibilidadeService,
            PapelVotacaoRepository papelVotacaoRepository,
            PropostaRepository propostaRepository,
            ParticipacaoRepository participacaoRepository,
            CedulaOrcamentoRepository cedulaOrcamentoRepository,
            AlocacaoOrcamentoRepository alocacaoOrcamentoRepository,
            ComprovanteService comprovanteService,
            Clock clock,
            MetricasVotacao metricasVotacao) {
        this.votacaoRepository = votacaoRepository;
        this.elegibilidadeService = elegibilidadeService;
        this.papelVotacaoRepository = papelVotacaoRepository;
        this.propostaRepository = propostaRepository;
        this.participacaoRepository = participacaoRepository;
        this.cedulaOrcamentoRepository = cedulaOrcamentoRepository;
        this.alocacaoOrcamentoRepository = alocacaoOrcamentoRepository;
        this.comprovanteService = comprovanteService;
        this.clock = clock;
        this.metricasVotacao = metricasVotacao;
    }

    /** Consulta o saldo de pontos total e restante do eleitor na votação. */
    @Transactional(readOnly = true)
    public SaldoOrcamentoResponse consultarSaldo(UUID votacaoId, Authentication autenticacao) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        exigirOrcamentoParticipativo(votacao);
        elegibilidadeService.validar(votacaoId, autenticacao);

        UUID usuarioId = usuarioDe(autenticacao);
        if (usuarioId == null) {
            throw new UsuarioNaoElegivelException(votacaoId);
        }

        BigDecimal peso = pesoDoEleitor(usuarioId, votacaoId);
        BigDecimal orcamentoTotal =
                votacao.getOrcamentoTotal() != null ? votacao.getOrcamentoTotal() : BigDecimal.ZERO;
        BigDecimal saldoTotal = orcamentoTotal.multiply(peso);

        boolean jaVotou =
                participacaoRepository.existsByUsuarioIdAndVotacaoId(usuarioId, votacaoId);
        BigDecimal saldoDisponivel = jaVotou ? BigDecimal.ZERO : saldoTotal;

        return new SaldoOrcamentoResponse(votacaoId, saldoTotal, saldoDisponivel, jaVotou);
    }

    /**
     * Registra o voto de orçamento participativo distribuindo pontos entre as propostas.
     *
     * @throws RecursoNaoEncontradoException (404) se a votação não existe
     * @throws UsuarioNaoElegivelException (403) se o usuário não é eleitor
     * @throws VotoForaDaJanelaException (409) se a votação não está em EmVotacao ou passou do prazo
     * @throws VotoDuplicadoException (409) se o eleitor já votou
     * @throws DadosInvalidosException (400) se propostas são de outra votação ou pontos negativos
     * @throws RegraDeNegocioException (400) se a soma dos pontos excede o saldo disponível
     */
    @Transactional
    public ComprovanteResponse registrar(
            UUID votacaoId, VotoOrcamentoRequest request, Authentication autenticacao) {
        try {
            Votacao votacao =
                    votacaoRepository
                            .buscarParaVoto(votacaoId)
                            .orElseThrow(
                                    () -> new RecursoNaoEncontradoException("Votação", votacaoId));
            exigirOrcamentoParticipativo(votacao);
            elegibilidadeService.validar(votacaoId, autenticacao);

            UUID usuarioId = usuarioDe(autenticacao);
            if (usuarioId == null) {
                throw new UsuarioNaoElegivelException(votacaoId);
            }

            Instant agora = clock.instant();
            exigirJanelaAberta(votacao, agora);

            if (participacaoRepository.existsByUsuarioIdAndVotacaoId(usuarioId, votacaoId)) {
                throw new VotoDuplicadoException(votacaoId);
            }

            BigDecimal peso = pesoDoEleitor(usuarioId, votacaoId);
            BigDecimal orcamentoTotal =
                    votacao.getOrcamentoTotal() != null
                            ? votacao.getOrcamentoTotal()
                            : BigDecimal.ZERO;
            BigDecimal saldoTotal = orcamentoTotal.multiply(peso);

            Map<UUID, BigDecimal> pontosPorProposta = validarEAgruparAlocacoes(votacaoId, request);
            BigDecimal totalAlocado =
                    pontosPorProposta.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalAlocado.compareTo(saldoTotal) > 0) {
                BigDecimal excesso = totalAlocado.subtract(saldoTotal);
                throw new RegraDeNegocioException(
                        "A soma dos pontos alocados (%s) excede o saldo disponível (%s) em %s pontos."
                                .formatted(totalAlocado, saldoTotal, excesso));
            }

            try {
                participacaoRepository.saveAndFlush(new Participacao(usuarioId, votacaoId));
            } catch (DataIntegrityViolationException e) {
                if (violouUnicidadeDoEleitor(e)) {
                    throw new VotoDuplicadoException(votacaoId);
                }
                throw e;
            }

            CedulaOrcamento cedula = cedulaOrcamentoRepository.save(new CedulaOrcamento(votacaoId));
            for (Map.Entry<UUID, BigDecimal> entry : pontosPorProposta.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    alocacaoOrcamentoRepository.save(
                            new AlocacaoOrcamento(
                                    cedula.getId(), entry.getKey(), entry.getValue()));
                }
            }

            String comprovante = comprovanteService.gerar(votacaoId, cedula.getId(), agora);
            metricasVotacao.votoRegistrado();
            return new ComprovanteResponse(votacaoId, comprovante, agora);
        } catch (RuntimeException e) {
            metricasVotacao.falhaRegistroVoto(e);
            throw e;
        }
    }

    private Map<UUID, BigDecimal> validarEAgruparAlocacoes(
            UUID votacaoId, VotoOrcamentoRequest request) {
        if (request == null || request.alocacoes() == null || request.alocacoes().isEmpty()) {
            throw new DadosInvalidosException(
                    "É necessário informar ao menos uma alocação de pontos.");
        }

        Map<UUID, BigDecimal> pontosPorProposta = new HashMap<>();
        for (AlocacaoPontoRequest alocacao : request.alocacoes()) {
            if (alocacao == null || alocacao.propostaId() == null || alocacao.pontos() == null) {
                throw new DadosInvalidosException("Dados de alocação de proposta inválidos.");
            }
            if (alocacao.pontos().compareTo(BigDecimal.ZERO) < 0) {
                throw new DadosInvalidosException("A pontuação alocada não pode ser negativa.");
            }
            if (propostaRepository
                    .findByIdAndVotacaoId(alocacao.propostaId(), votacaoId)
                    .isEmpty()) {
                throw new DadosInvalidosException(
                        "A proposta %s não pertence à votação %s."
                                .formatted(alocacao.propostaId(), votacaoId));
            }
            pontosPorProposta.merge(alocacao.propostaId(), alocacao.pontos(), BigDecimal::add);
        }
        return pontosPorProposta;
    }

    private void exigirOrcamentoParticipativo(Votacao votacao) {
        if (votacao.getTipo() != TipoVotacao.ORCAMENTO_PARTICIPATIVO) {
            throw new RegraDeNegocioException(
                    "Esta operação é exclusiva para votações de Orçamento Participativo.");
        }
    }

    private void exigirJanelaAberta(Votacao votacao, Instant agora) {
        boolean estadoPermite =
                ComportamentosDeEstado.de(votacao.getEstado()).permite(OperacaoVotacao.VOTAR);
        if (!estadoPermite || !agora.isBefore(votacao.getFimEm())) {
            throw new VotoForaDaJanelaException(votacao.getId(), votacao.getEstado());
        }
    }

    private BigDecimal pesoDoEleitor(UUID usuarioId, UUID votacaoId) {
        return papelVotacaoRepository
                .findById(new PapelVotacaoId(usuarioId, votacaoId, PapelUsuario.ELEITOR))
                .map(PapelVotacao::getPeso)
                .orElseThrow(() -> new UsuarioNaoElegivelException(votacaoId));
    }

    private static boolean violouUnicidadeDoEleitor(DataIntegrityViolationException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && UNIQUE_ELEITOR.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static UUID usuarioDe(Authentication autenticacao) {
        if (autenticacao instanceof JwtAuthenticationToken token) {
            String sub = token.getToken().getSubject();
            if (sub != null) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }
        if (autenticacao != null
                && autenticacao.isAuthenticated()
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.getId();
        }
        if (autenticacao != null && autenticacao.getName() != null) {
            try {
                return UUID.fromString(autenticacao.getName());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
