package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.AlocacaoApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.Apuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.ApuracaoIniciada;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.Cedula;
import com.unicamp.engsoft.eleicao.votacao.domain.CedulaOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracaoOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoDesempate;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.AlocacaoOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaOrcamentoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CedulaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integra os motores de apuração com votos persistidos (#50, #130). O evento é síncrono: estado,
 * auditoria, snapshot e eventual segundo turno/anulação fazem commit ou rollback juntos.
 */
@Service
public class ApuracaoService {
    private final VotacaoRepository votacoes;
    private final ApuracaoRepository apuracoes;
    private final CedulaRepository cedulas;
    private final CedulaOrcamentoRepository cedulasOrcamento;
    private final AlocacaoOrcamentoRepository alocacoesOrcamento;
    private final ParticipacaoRepository participacoes;
    private final CandidatoOuChapaRepository candidatos;
    private final PropostaRepository propostas;
    private final PapelVotacaoRepository papeis;
    private final MotorApuracao motor;
    private final MotorApuracaoOrcamento motorOrcamento;
    private final EstrategiaDesempateDaVotacao desempates;
    private final AnulacaoVotacaoService anulacao;
    private final Clock clock;

    public ApuracaoService(
            VotacaoRepository votacoes,
            ApuracaoRepository apuracoes,
            CedulaRepository cedulas,
            CedulaOrcamentoRepository cedulasOrcamento,
            AlocacaoOrcamentoRepository alocacoesOrcamento,
            ParticipacaoRepository participacoes,
            CandidatoOuChapaRepository candidatos,
            PropostaRepository propostas,
            PapelVotacaoRepository papeis,
            MotorApuracao motor,
            MotorApuracaoOrcamento motorOrcamento,
            EstrategiaDesempateDaVotacao desempates,
            AnulacaoVotacaoService anulacao,
            Clock clock) {
        this.votacoes = votacoes;
        this.apuracoes = apuracoes;
        this.cedulas = cedulas;
        this.cedulasOrcamento = cedulasOrcamento;
        this.alocacoesOrcamento = alocacoesOrcamento;
        this.participacoes = participacoes;
        this.candidatos = candidatos;
        this.propostas = propostas;
        this.papeis = papeis;
        this.motor = motor;
        this.motorOrcamento = motorOrcamento;
        this.desempates = desempates;
        this.anulacao = anulacao;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void aoIniciar(ApuracaoIniciada evento) {
        apurar(carregarComLock(evento.votacaoId()));
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
        if (existente.isPresent()) {
            return existente.get();
        }
        if (votacao.getEstado() != EstadoVotacao.Apuracao) {
            throw new RegraDeNegocioException("Apenas votação em Apuracao pode ser apurada");
        }
        long totalParticipantes = participacoes.countByVotacaoId(id);
        long eleitoresAptos = papeis.countByIdVotacaoIdAndIdPapel(id, PapelUsuario.ELEITOR);
        if (votacao.getTipo() == TipoVotacao.ORCAMENTO_PARTICIPATIVO) {
            return apurarOrcamento(votacao, totalParticipantes, eleitoresAptos);
        }
        return apurarEleicao(votacao, totalParticipantes, eleitoresAptos);
    }

    private Apuracao apurarEleicao(Votacao votacao, long totalParticipantes, long eleitoresAptos) {
        UUID id = votacao.getId();
        if (votacao.getRegras() == null) {
            throw new RegraDeNegocioException("Não é possível apurar sem regras configuradas");
        }
        List<Cedula> votos = cedulas.findByVotacaoId(id);
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
                        eleitoresAptos,
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

    private Apuracao apurarOrcamento(
            Votacao votacao, long totalParticipantes, long eleitoresAptos) {
        UUID id = votacao.getId();
        if (votacao.getOrcamentoTotal() == null) {
            throw new RegraDeNegocioException(
                    "Não é possível apurar orçamento participativo sem orçamento total definido");
        }
        List<CedulaOrcamento> cedulasOp = cedulasOrcamento.findByVotacaoId(id);
        if (totalParticipantes != cedulasOp.size()) {
            throw new RegraDeNegocioException(
                    "Quantidade de cédulas difere da quantidade de participações");
        }
        List<PropostaApuravel> listaPropostas =
                propostas.findByVotacaoIdOrderByCriadoEm(id).stream()
                        .map(
                                p ->
                                        new PropostaApuravel(
                                                p.getId(),
                                                p.getTitulo(),
                                                p.getCusto(),
                                                p.getCriadoEm()))
                        .toList();
        if (listaPropostas.isEmpty()) {
            throw new RegraDeNegocioException(
                    "Não é possível apurar orçamento participativo sem propostas cadastradas");
        }
        List<AlocacaoApuravel> alocacoes =
                cedulasOp.isEmpty()
                        ? List.of()
                        : alocacoesOrcamento
                                .findByCedulaIdIn(
                                        cedulasOp.stream().map(CedulaOrcamento::getId).toList())
                                .stream()
                                .map(a -> new AlocacaoApuravel(a.getPropostaId(), a.getPontos()))
                                .toList();
        BigDecimal quorumMinimo =
                votacao.getRegras() != null ? votacao.getRegras().quorumMinimoPercentual() : null;
        TipoDesempate tipoDesempate =
                votacao.getRegras() != null
                        ? votacao.getRegras().tipoDesempate()
                        : TipoDesempate.NENHUM;
        ResultadoApuracaoOrcamento resultadoOrcamento =
                motorOrcamento.apurar(
                        votacao.getOrcamentoTotal(),
                        quorumMinimo,
                        listaPropostas,
                        alocacoes,
                        eleitoresAptos,
                        totalParticipantes,
                        new DesempateMenorCusto(tipoDesempate));
        Apuracao snapshot =
                apuracoes.save(
                        Apuracao.deOrcamento(
                                id,
                                clock.instant().truncatedTo(ChronoUnit.MICROS),
                                votacao.getRegras(),
                                resultadoOrcamento,
                                cedulasOp.size()));
        if (!resultadoOrcamento.quorumAtingido()) {
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
