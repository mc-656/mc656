package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.Apuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.ApuracaoIniciada;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.Cedula;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integra o motor com votos persistidos (#50). O evento é síncrono: estado, auditoria, snapshot e
 * eventual segundo turno/anulação fazem commit ou rollback juntos.
 *
 * <p>O lock exclusivo da votação serializa chamadas e espera votos em andamento (que usam lock
 * compartilhado). Todas as cédulas são lidas, sem filtros; nenhuma nova cédula pode ser registrada
 * pelo serviço de voto depois dessa transição. A PK do snapshot e a UNIQUE da votação derivada
 * reforçam a idempotência no banco.
 */
@Service
public class ApuracaoService {
    private final VotacaoRepository votacoes;
    private final ApuracaoRepository apuracoes;
    private final CedulaRepository cedulas;
    private final ParticipacaoRepository participacoes;
    private final CandidatoOuChapaRepository candidatos;
    private final PapelVotacaoRepository papeis;
    private final MotorApuracao motor;
    private final EstrategiaDesempateDaVotacao desempates;
    private final AnulacaoVotacaoService anulacao;
    private final Clock clock;

    public ApuracaoService(
            VotacaoRepository votacoes,
            ApuracaoRepository apuracoes,
            CedulaRepository cedulas,
            ParticipacaoRepository participacoes,
            CandidatoOuChapaRepository candidatos,
            PapelVotacaoRepository papeis,
            MotorApuracao motor,
            EstrategiaDesempateDaVotacao desempates,
            AnulacaoVotacaoService anulacao,
            Clock clock) {
        this.votacoes = votacoes;
        this.apuracoes = apuracoes;
        this.cedulas = cedulas;
        this.participacoes = participacoes;
        this.candidatos = candidatos;
        this.papeis = papeis;
        this.motor = motor;
        this.desempates = desempates;
        this.anulacao = anulacao;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void aoIniciar(ApuracaoIniciada evento) {
        Votacao votacao = carregarComLock(evento.votacaoId());
        // Orçamento participativo terá seu motor de cotas na #130.
        if (votacao.getTipo() == TipoVotacao.ELEICAO_PRIVADA) {
            apurar(votacao);
        }
    }

    /**
     * Reprocessamento interno para votações já em Apuracao; nunca recalcula um snapshot existente.
     */
    @Transactional
    public Apuracao apurar(UUID votacaoId) {
        return apurar(carregarComLock(votacaoId));
    }

    private Votacao carregarComLock(UUID id) {
        return votacoes.buscarParaTransicao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", id));
    }

    private Apuracao apurar(Votacao votacao) {
        UUID id = votacao.getId();
        var existente = apuracoes.findById(id);
        if (existente.isPresent()) return existente.get();
        if (votacao.getTipo() != TipoVotacao.ELEICAO_PRIVADA
                || votacao.getEstado() != EstadoVotacao.Apuracao) {
            throw new RegraDeNegocioException(
                    "Apenas eleição privada em Apuracao pode ser apurada");
        }
        if (votacao.getRegras() == null) {
            throw new RegraDeNegocioException("Não é possível apurar sem regras configuradas");
        }
        List<Cedula> votos = cedulas.findByVotacaoId(id);
        long totalParticipantes = participacoes.countByVotacaoId(id);
        if (totalParticipantes != votos.size()) {
            throw new RegraDeNegocioException(
                    "Quantidade de cédulas difere da quantidade de participações");
        }
        List<CandidatoOuChapa> opcoes = candidatos.findByVotacaoIdOrderByCriadoEm(id);
        var idsOpcoes = opcoes.stream().map(CandidatoOuChapa::getId).collect(Collectors.toSet());
        if (votos.stream().anyMatch(v -> !idsOpcoes.contains(v.getOpcaoId()))) {
            throw new RegraDeNegocioException("Cédula contém opção que não pertence à votação");
        }
        ResultadoApuracao resultado =
                motor.apurar(
                        votacao.getRegras(),
                        votos.stream().map(Cedula::paraApuracao).toList(),
                        idsOpcoes,
                        papeis.countByIdVotacaoIdAndIdPapel(id, PapelUsuario.ELEITOR),
                        totalParticipantes,
                        desempates.criar(votacao, opcoes));
        UUID segundoTurno =
                resultado.status() == StatusApuracao.SEGUNDO_TURNO
                        ? criarSegundoTurno(votacao, resultado, opcoes)
                        : null;
        Apuracao snapshot =
                apuracoes.save(
                        new Apuracao(
                                id,
                                clock.instant().truncatedTo(ChronoUnit.MICROS),
                                votacao.getRegras(),
                                resultado,
                                votos.size(),
                                segundoTurno));
        if (!resultado.quorumAtingido()) {
            anulacao.anularPorFaltaDeQuorum(id);
        }
        return snapshot;
    }

    private UUID criarSegundoTurno(
            Votacao origem, ResultadoApuracao resultado, List<CandidatoOuChapa> opcoes) {
        Votacao derivada = votacoes.save(origem.criarSegundoTurno());
        for (UUID finalista : resultado.finalistasSegundoTurno()) {
            CandidatoOuChapa opcao =
                    opcoes.stream()
                            .filter(o -> o.getId().equals(finalista))
                            .findFirst()
                            .orElseThrow();
            candidatos.save(
                    new CandidatoOuChapa(
                            derivada.getId(),
                            opcao.getNome(),
                            opcao.getDescricao(),
                            opcao.getDataNascimento()));
        }
        for (PapelVotacao papel : papeis.findByIdVotacaoId(origem.getId())) {
            papeis.save(
                    new PapelVotacao(
                            papel.getId().getUsuarioId(),
                            derivada.getId(),
                            papel.getId().getPapel(),
                            papel.getPeso()));
        }
        return derivada.getId();
    }
}
