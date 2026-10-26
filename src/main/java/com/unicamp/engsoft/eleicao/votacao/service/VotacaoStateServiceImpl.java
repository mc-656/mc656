package com.unicamp.engsoft.eleicao.votacao.service;

import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Apuracao;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Cancelada;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.EmVotacao;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Publicada;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.RascunhoVotacao;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Suspensa;
import static com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao.SISTEMA;
import static com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao.USUARIO;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.TransicaoVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto de transição de estado de {@link Votacao} (SPECS §7.3), com auditoria de cada
 * transição (RNF-03).
 *
 * <p>As transições válidas ficam em {@link #TRANSICOES}: estado de origem, destinos permitidos e
 * quem pode disparar cada uma. A origem entra na tabela porque há transições que só a passagem do
 * tempo pode causar ({@code Publicada → EmVotacao}): o admin não pode abrir a votação antes da
 * data. Novas fases só acrescentam linhas.
 *
 * <p>Fase de votação (#55, SPECS §4.2): {@code EmVotacao ⇄ Suspensa} por ação do admin, {@code
 * EmVotacao}/{@code Suspensa → Cancelada} (cancelamento forçado) e {@code EmVotacao → Apuracao} só
 * por data. {@code Suspensa → Apuracao} não existe de propósito: com o fim vencido durante a
 * suspensão, a votação espera o admin decidir (prorrogar e retomar, cancelar ou retomar sem
 * prorrogar, e aí o job de encerramento a leva para {@code Apuracao}). Os subestados {@code
 * RecebendoVotos}, {@code ComputandoPesos} e {@code ValidandoCotas} são conceituais e não entram na
 * tabela. {@code Cancelada} não tem linha: é estado final.
 */
@Service
public class VotacaoStateServiceImpl implements VotacaoStateService {

    static final Map<EstadoVotacao, Map<EstadoVotacao, Set<OrigemTransicao>>> TRANSICOES =
            Map.of(
                    RascunhoVotacao, Map.of(Publicada, Set.of(USUARIO)),
                    Publicada,
                            Map.of(
                                    RascunhoVotacao, Set.of(USUARIO),
                                    EmVotacao, Set.of(SISTEMA),
                                    Cancelada, Set.of(USUARIO)),
                    EmVotacao,
                            Map.of(
                                    Suspensa, Set.of(USUARIO),
                                    Cancelada, Set.of(USUARIO),
                                    Apuracao, Set.of(SISTEMA)),
                    Suspensa,
                            Map.of(
                                    EmVotacao, Set.of(USUARIO),
                                    Cancelada, Set.of(USUARIO)));

    /**
     * Transições que interrompem uma votação em andamento: sem justificativa registrada, a
     * auditoria (RNF-03) não explica aos eleitores por que a votação parou. O cancelamento antes do
     * início ({@code Publicada → Cancelada}) segue opcional, como na #24.
     */
    static final Map<EstadoVotacao, Set<EstadoVotacao>> EXIGEM_MOTIVO =
            Map.of(EmVotacao, Set.of(Suspensa, Cancelada), Suspensa, Set.of(Cancelada));

    /**
     * Tamanho da coluna {@code motivo} da V7. Conferido também aqui (além do {@code @Size} do DTO)
     * porque o registro da prorrogação aumenta o texto e porque nem todo chamador passa pelo
     * controller.
     */
    static final int TAMANHO_MAXIMO_MOTIVO = 500;

    private final VotacaoRepository votacaoRepository;
    private final TransicaoVotacaoRepository transicaoRepository;
    private final List<GuardaPublicacao> guardasPublicacao;
    private final Clock clock;

    public VotacaoStateServiceImpl(
            VotacaoRepository votacaoRepository,
            TransicaoVotacaoRepository transicaoRepository,
            List<GuardaPublicacao> guardasPublicacao,
            Clock clock) {
        this.votacaoRepository = votacaoRepository;
        this.transicaoRepository = transicaoRepository;
        this.guardasPublicacao = guardasPublicacao;
        this.clock = clock;
    }

    /**
     * Implementada aqui, com o próprio {@code @Transactional}, e não como método {@code default} da
     * interface: a chamada interna à sobrecarga não passa pelo proxy do Spring, então só esta
     * anotação garante a transação (e o lock de {@code buscarParaTransicao}) para os jobs.
     */
    @Override
    @Transactional
    public void transitar(UUID votacaoId, EstadoVotacao destino, UUID autorId, String motivo) {
        transitar(votacaoId, destino, autorId, motivo, null);
    }

    @Override
    @Transactional
    public void transitar(
            UUID votacaoId, EstadoVotacao destino, UUID autorId, String motivo, Instant novoFimEm) {
        Votacao votacao =
                votacaoRepository
                        .buscarParaTransicao(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        EstadoVotacao atual = votacao.getEstado();
        OrigemTransicao origem = autorId == null ? SISTEMA : USUARIO;

        Set<OrigemTransicao> permitidas = TRANSICOES.getOrDefault(atual, Map.of()).get(destino);
        if (permitidas == null) {
            throw new TransicaoInvalidaException(
                    votacaoId, atual, destino, "transição não prevista na máquina de estados");
        }
        if (!permitidas.contains(origem)) {
            throw new TransicaoInvalidaException(
                    votacaoId,
                    atual,
                    destino,
                    origem == USUARIO
                            ? "transição ocorre apenas automaticamente, por data"
                            : "transição exige ação de um usuário");
        }
        Instant agora = clock.instant();
        if (destino == Apuracao && votacao.getFimEm().isAfter(agora)) {
            throw new TransicaoInvalidaException(
                    votacaoId, atual, destino, "data de fim ainda não atingida");
        }
        if (EXIGEM_MOTIVO.getOrDefault(atual, Set.of()).contains(destino)
                && (motivo == null || motivo.isBlank())) {
            throw new DadosInvalidosException(
                    "Motivo é obrigatório para a transição de " + atual + " para " + destino);
        }
        String motivoAuditado = motivo;
        if (novoFimEm != null) {
            validarProrrogacao(votacao, atual, destino, novoFimEm, agora);
            motivoAuditado = comProrrogacao(motivo, votacao.getFimEm(), novoFimEm);
        }
        if (motivoAuditado != null && motivoAuditado.length() > TAMANHO_MAXIMO_MOTIVO) {
            throw new DadosInvalidosException(
                    "Motivo excede "
                            + TAMANHO_MAXIMO_MOTIVO
                            + " caracteres"
                            + (novoFimEm != null ? " junto com o registro da prorrogação" : ""));
        }
        if (destino == Publicada) {
            guardasPublicacao.forEach(guarda -> guarda.validar(votacaoId));
        }

        if (novoFimEm != null) {
            votacao.estenderFim(novoFimEm);
        }
        votacao.alterarEstado(destino);
        transicaoRepository.save(
                new TransicaoVotacao(votacaoId, atual, destino, autorId, motivoAuditado, agora));
    }

    /**
     * A prorrogação só existe na retomada (SPECS §4.2) e precisa de fato reabrir a votação: um fim
     * já vencido faria o job de encerramento levá-la para {@code Apuracao} logo em seguida, o que
     * equivale a retomar sem prorrogar.
     */
    private static void validarProrrogacao(
            Votacao votacao,
            EstadoVotacao atual,
            EstadoVotacao destino,
            Instant novoFimEm,
            Instant agora) {
        if (atual != Suspensa || destino != EmVotacao) {
            throw new TransicaoInvalidaException(
                    votacao.getId(),
                    atual,
                    destino,
                    "a data de fim só pode ser prorrogada ao retomar uma votação suspensa");
        }
        if (!novoFimEm.isAfter(votacao.getFimEm())) {
            throw new DadosInvalidosException(
                    "A nova data de fim deve ser posterior à data de fim atual");
        }
        if (!novoFimEm.isAfter(agora)) {
            throw new DadosInvalidosException("A nova data de fim deve estar no futuro");
        }
    }

    /**
     * A V7 não tem coluna para a data anterior, e a prorrogação precisa ficar na auditoria (RNF-03)
     * sem migração nova: ela vai no texto do motivo. Nunca truncamos a justificativa do admin; se o
     * conjunto não couber na coluna, a transição é rejeitada.
     */
    static String comProrrogacao(String motivo, Instant fimAnterior, Instant novoFim) {
        String registro = "[fim prorrogado de " + fimAnterior + " para " + novoFim + "]";
        if (motivo == null || motivo.isBlank()) {
            return registro;
        }
        return motivo + " " + registro;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransicaoVotacao> historico(UUID votacaoId) {
        return transicaoRepository.findByVotacaoIdOrderByOcorridaEmAsc(votacaoId);
    }
}
