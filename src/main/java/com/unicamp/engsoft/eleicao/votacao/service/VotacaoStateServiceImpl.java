package com.unicamp.engsoft.eleicao.votacao.service;

import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Cancelada;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.EmVotacao;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.Publicada;
import static com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao.RascunhoVotacao;
import static com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao.SISTEMA;
import static com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao.USUARIO;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.TransicaoVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
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
 * data. Novas fases (#55) só acrescentam linhas.
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
                                    Cancelada, Set.of(USUARIO)));

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

    @Override
    @Transactional
    public void transitar(UUID votacaoId, EstadoVotacao destino, UUID autorId, String motivo) {
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
        if (destino == Publicada) {
            guardasPublicacao.forEach(guarda -> guarda.validar(votacaoId));
        }

        votacao.alterarEstado(destino);
        transicaoRepository.save(
                new TransicaoVotacao(votacaoId, atual, destino, autorId, motivo, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransicaoVotacao> historico(UUID votacaoId) {
        return transicaoRepository.findByVotacaoIdOrderByOcorridaEmAsc(votacaoId);
    }
}
