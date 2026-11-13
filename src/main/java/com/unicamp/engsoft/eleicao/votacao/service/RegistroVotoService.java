package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.Cedula;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.Participacao;
import com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoDuplicadoException;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoForaDaJanelaException;
import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificacaoComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro do voto único com comprovante (RF-13) e bloqueio de voto duplicado ou fora da janela
 * (RF-14).
 *
 * <p><b>Ordem das checagens.</b> Votação inexistente (404) e eleitor não elegível (403) vêm antes
 * do estado, para quem não participa da votação não descobrir em que fase ela está. Depois a janela
 * ({@link VotoForaDaJanelaException}) e só então o duplicado ({@link VotoDuplicadoException}): fora
 * da janela ninguém vota, tenha votado antes ou não.
 *
 * <p><b>Sigilo (RNF-02).</b> {@link Participacao} e {@link Cedula} são gravadas na mesma transação,
 * sem coluna ou relação que as ligue. Este serviço não devolve, nem a quem votou nem ao admin, a
 * opção de uma cédula: o comprovante atesta só que uma cédula foi registrada naquela votação. Risco
 * residual: quem lê o banco direto ainda pode correlacionar as linhas por colunas de sistema do
 * Postgres (xmin, ordem física); ver o comentário da V16.
 *
 * <p><b>Concorrência.</b> A votação é lida com lock compartilhado ({@link
 * VotacaoRepository#buscarParaVoto}), então uma suspensão ou encerramento concorrente espera este
 * voto terminar, ou este voto espera a transição e enxerga o estado novo. Dois votos simultâneos do
 * mesmo eleitor passam pela checagem de duplicado, mas o segundo esbarra na {@code UNIQUE} de
 * {@code participacoes} e vira o mesmo {@link VotoDuplicadoException}.
 */
@Service
public class RegistroVotoService {

    /** Nome da constraint da V16 que impede segunda participação do mesmo eleitor. */
    private static final String UNIQUE_ELEITOR = "participacoes_eleitor_unico";

    private final VotacaoRepository votacaoRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final PapelVotacaoRepository papelVotacaoRepository;
    private final CandidatoOuChapaRepository candidatoOuChapaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final CedulaRepository cedulaRepository;
    private final ComprovanteService comprovanteService;
    private final Clock clock;

    public RegistroVotoService(
            VotacaoRepository votacaoRepository,
            ElegibilidadeService elegibilidadeService,
            PapelVotacaoRepository papelVotacaoRepository,
            CandidatoOuChapaRepository candidatoOuChapaRepository,
            ParticipacaoRepository participacaoRepository,
            CedulaRepository cedulaRepository,
            ComprovanteService comprovanteService,
            Clock clock) {
        this.votacaoRepository = votacaoRepository;
        this.elegibilidadeService = elegibilidadeService;
        this.papelVotacaoRepository = papelVotacaoRepository;
        this.candidatoOuChapaRepository = candidatoOuChapaRepository;
        this.participacaoRepository = participacaoRepository;
        this.cedulaRepository = cedulaRepository;
        this.comprovanteService = comprovanteService;
        this.clock = clock;
    }

    /**
     * Registra o voto do usuário autenticado e devolve o comprovante.
     *
     * @throws RecursoNaoEncontradoException (404) se a votação não existe
     * @throws com.unicamp.engsoft.eleicao.votacao.domain.UsuarioNaoElegivelException (403) se o
     *     usuário não é eleitor da votação
     * @throws VotoForaDaJanelaException (409) se a votação não está em {@code EmVotacao} ou o prazo
     *     de fim já passou
     * @throws VotoDuplicadoException (409) se o eleitor já votou nesta votação
     * @throws DadosInvalidosException (400) se a opção não pertence à votação
     */
    @Transactional
    public ComprovanteResponse registrar(
            UUID votacaoId, UUID opcaoId, Authentication autenticacao) {
        // Lock primeiro: a remoção de eleitor também trava a votação (FOR UPDATE), então o papel
        // checado logo abaixo não muda até o commit deste voto.
        Votacao votacao =
                votacaoRepository
                        .buscarParaVoto(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        elegibilidadeService.validar(votacaoId, autenticacao);
        // Elegível implica token JWT com sub em formato UUID (AutorizacaoVotacao nega o resto), e
        // no JwtAuthenticationToken o nome é o sub.
        UUID usuarioId = UUID.fromString(autenticacao.getName());

        Instant agora = clock.instant();
        exigirJanelaAberta(votacao, agora);

        if (participacaoRepository.existsByUsuarioIdAndVotacaoId(usuarioId, votacaoId)) {
            throw new VotoDuplicadoException(votacaoId);
        }
        if (candidatoOuChapaRepository.findByIdAndVotacaoId(opcaoId, votacaoId).isEmpty()) {
            throw new DadosInvalidosException(
                    "A opção %s não pertence à votação %s.".formatted(opcaoId, votacaoId));
        }
        BigDecimal peso = pesoDoEleitor(usuarioId, votacaoId);

        try {
            participacaoRepository.saveAndFlush(new Participacao(usuarioId, votacaoId));
        } catch (DataIntegrityViolationException e) {
            if (violouUnicidadeDoEleitor(e)) {
                // Corrida entre duas requisições do mesmo eleitor: a UNIQUE decidiu.
                throw new VotoDuplicadoException(votacaoId);
            }
            throw e;
        }
        // Sem flush: o id já existe após o persist e o commit grava a cédula.
        Cedula cedula = cedulaRepository.save(new Cedula(votacaoId, opcaoId, peso));

        String comprovante = comprovanteService.gerar(votacaoId, cedula.getId(), agora);
        return new ComprovanteResponse(votacaoId, comprovante, agora);
    }

    /**
     * Verifica um comprovante (RNF-01): além de a criptografia conferir, a cédula atestada precisa
     * existir. Não devolve a opção votada nem o id da cédula (RNF-02).
     *
     * @throws ComprovanteInvalidoException (400) se o comprovante foi adulterado, gerado com outra
     *     chave ou atesta uma cédula que não está gravada
     */
    @Transactional(readOnly = true)
    public VerificacaoComprovanteResponse verificar(String comprovante) {
        ComprovanteService.DadosComprovante dados = comprovanteService.verificar(comprovante);
        if (!cedulaRepository.existsByIdAndVotacaoId(dados.cedulaId(), dados.votacaoId())) {
            throw new ComprovanteInvalidoException();
        }
        return new VerificacaoComprovanteResponse(dados.votacaoId(), dados.emitidoEm());
    }

    /**
     * A janela é o estado {@code EmVotacao} (consultado pelo comportamento do estado, padrão State)
     * e, além dele, o prazo de fim: o encerramento automático roda periodicamente, e um voto entre
     * o fim e a próxima execução do job ainda estaria "dentro" pelo estado.
     */
    private void exigirJanelaAberta(Votacao votacao, Instant agora) {
        boolean estadoPermite =
                ComportamentosDeEstado.de(votacao.getEstado()).permite(OperacaoVotacao.VOTAR);
        if (!estadoPermite || !agora.isBefore(votacao.getFimEm())) {
            throw new VotoForaDaJanelaException(votacao.getId(), votacao.getEstado());
        }
    }

    /**
     * Peso do papel {@code ELEITOR}. A elegibilidade acabou de passar sob o lock, então o papel
     * existe; se faltar, é recusa e não peso 1 silencioso, que distorceria a apuração ponderada.
     */
    private BigDecimal pesoDoEleitor(UUID usuarioId, UUID votacaoId) {
        return papelVotacaoRepository
                .findById(new PapelVotacaoId(usuarioId, votacaoId, PapelUsuario.ELEITOR))
                .map(PapelVotacao::getPeso)
                .orElseThrow(() -> new UsuarioNaoElegivelException(votacaoId));
    }

    /** Só a {@code UNIQUE (usuario_id, votacao_id)} vira voto duplicado; outra violação sobe. */
    private static boolean violouUnicidadeDoEleitor(DataIntegrityViolationException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && UNIQUE_ELEITOR.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
