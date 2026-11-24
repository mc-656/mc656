package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.Apuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracaoOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusPropostaApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse.AnulacaoInfo;
import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse.HomologacaoInfo;
import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse.OpcaoResultadoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.ResultadoVotacaoResponse.OrcamentoResultadoInfo;
import com.unicamp.engsoft.eleicao.votacao.repository.ApuracaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.CandidatoOuChapaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.ParticipacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.TransicaoVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta ao resultado oficial e detalhes de homologação ou anulação de uma votação (RF-41, RF-42,
 * RF-23, #52, #84, #85, #130).
 */
@Service
public class ResultadoVotacaoService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final VotacaoRepository votacaoRepository;
    private final ApuracaoRepository apuracaoRepository;
    private final CandidatoOuChapaRepository candidatoRepository;
    private final PropostaRepository propostaRepository;
    private final PapelVotacaoRepository papelVotacaoRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final TransicaoVotacaoRepository transicaoVotacaoRepository;

    public ResultadoVotacaoService(
            VotacaoRepository votacaoRepository,
            ApuracaoRepository apuracaoRepository,
            CandidatoOuChapaRepository candidatoRepository,
            PropostaRepository propostaRepository,
            PapelVotacaoRepository papelVotacaoRepository,
            ParticipacaoRepository participacaoRepository,
            TransicaoVotacaoRepository transicaoVotacaoRepository) {
        this.votacaoRepository = votacaoRepository;
        this.apuracaoRepository = apuracaoRepository;
        this.candidatoRepository = candidatoRepository;
        this.propostaRepository = propostaRepository;
        this.papelVotacaoRepository = papelVotacaoRepository;
        this.participacaoRepository = participacaoRepository;
        this.transicaoVotacaoRepository = transicaoVotacaoRepository;
    }

    @Transactional(readOnly = true)
    public ResultadoVotacaoResponse consultarResultado(UUID votacaoId, UUID usuarioId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        EstadoVotacao estado = votacao.getEstado();
        boolean ehAdmin =
                usuarioId != null
                        && papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                usuarioId, votacaoId, PapelUsuario.ADMIN_VOTACAO);
        return switch (estado) {
            case Encerrada, Homologada -> montarResultado(votacao);
            case Apuracao -> {
                if (!ehAdmin) {
                    throw new RegraDeNegocioException(
                            "O resultado da votação ainda não foi publicado para os eleitores");
                }
                yield montarResultado(votacao);
            }
            case Anulada -> montarResultadoAnulada(votacao);
            case RascunhoVotacao, Publicada, EmVotacao, Suspensa, Cancelada ->
                    throw new RegraDeNegocioException(
                            "Resultado indisponível para votação no estado " + estado);
        };
    }

    private ResultadoVotacaoResponse montarResultado(Votacao votacao) {
        UUID votacaoId = votacao.getId();
        Apuracao apuracao =
                apuracaoRepository
                        .findById(votacaoId)
                        .orElseThrow(
                                () ->
                                        new RegraDeNegocioException(
                                                "A apuração da votação ainda não foi concluída"));
        HomologacaoInfo homologacao =
                votacao.getEstado() == EstadoVotacao.Homologada
                        ? buscarHomologacao(votacaoId)
                        : null;
        if (apuracao.getResultadoOrcamento() != null) {
            return montarResultadoOrcamento(
                    votacao, apuracao, apuracao.getResultadoOrcamento(), homologacao);
        }
        ResultadoApuracao resultado = apuracao.getResultado();
        if (resultado == null) {
            throw new RegraDeNegocioException("Resultado da apuração não encontrado");
        }
        Map<UUID, String> nomesPorOpcao =
                candidatoRepository.findByVotacaoIdOrderByCriadoEm(votacaoId).stream()
                        .collect(
                                Collectors.toMap(
                                        CandidatoOuChapa::getId, CandidatoOuChapa::getNome));
        List<UUID> finalistasLista =
                resultado.finalistasSegundoTurno() == null
                        ? List.of()
                        : resultado.finalistasSegundoTurno();
        Set<UUID> finalistas = Set.copyOf(finalistasLista);
        List<OpcaoResultadoResponse> opcoes =
                resultado.ranking().stream()
                        .map(
                                opcao ->
                                        new OpcaoResultadoResponse(
                                                opcao.opcaoId(),
                                                nomesPorOpcao.getOrDefault(
                                                        opcao.opcaoId(), "Opção"),
                                                opcao.totalVotos(),
                                                opcao.percentualVotosValidos(),
                                                opcao.opcaoId().equals(resultado.vencedorId()),
                                                finalistas.contains(opcao.opcaoId()),
                                                null,
                                                null))
                        .toList();
        String vencedorNome =
                resultado.vencedorId() == null ? null : nomesPorOpcao.get(resultado.vencedorId());
        BigDecimal quorumMinimo =
                apuracao.getRegra() != null ? apuracao.getRegra().quorumMinimoPercentual() : null;
        return new ResultadoVotacaoResponse(
                votacaoId,
                votacao.getNome(),
                votacao.getTipo(),
                votacao.getEstado(),
                resultado.status(),
                apuracao.getConcluidaEm(),
                resultado.vencedorId(),
                vencedorNome,
                finalistasLista,
                apuracao.getSegundoTurnoId(),
                resultado.totalParticipantes(),
                resultado.totalEleitoresAptos(),
                resultado.percentualComparecimento(),
                quorumMinimo,
                resultado.quorumAtingido(),
                resultado.totalVotosValidos(),
                apuracao.getTotalCedulas(),
                opcoes,
                homologacao,
                null,
                null);
    }

    private ResultadoVotacaoResponse montarResultadoOrcamento(
            Votacao votacao,
            Apuracao apuracao,
            ResultadoApuracaoOrcamento resultado,
            HomologacaoInfo homologacao) {
        UUID votacaoId = votacao.getId();
        Map<UUID, String> titulosPorProposta =
                propostaRepository.findByVotacaoIdOrderByCriadoEm(votacaoId).stream()
                        .collect(Collectors.toMap(Proposta::getId, Proposta::getTitulo));
        BigDecimal totalPontos = resultado.totalPontosComputados();
        List<OpcaoResultadoResponse> opcoes =
                resultado.propostas().stream()
                        .map(
                                p -> {
                                    BigDecimal percentual =
                                            totalPontos.signum() == 0
                                                    ? BigDecimal.ZERO.setScale(
                                                            2, RoundingMode.HALF_UP)
                                                    : p.totalPontos()
                                                            .multiply(CEM)
                                                            .divide(
                                                                    totalPontos,
                                                                    2,
                                                                    RoundingMode.HALF_UP);
                                    return new OpcaoResultadoResponse(
                                            p.propostaId(),
                                            titulosPorProposta.getOrDefault(
                                                    p.propostaId(), "Proposta"),
                                            p.totalPontos(),
                                            percentual,
                                            p.status() == StatusPropostaApurada.SELECIONADA,
                                            false,
                                            p.custo(),
                                            p.status());
                                })
                        .toList();
        BigDecimal quorumMinimo =
                apuracao.getRegra() != null ? apuracao.getRegra().quorumMinimoPercentual() : null;
        return new ResultadoVotacaoResponse(
                votacaoId,
                votacao.getNome(),
                votacao.getTipo(),
                votacao.getEstado(),
                resultado.status(),
                apuracao.getConcluidaEm(),
                null,
                null,
                List.of(),
                null,
                resultado.totalParticipantes(),
                resultado.totalEleitoresAptos(),
                resultado.percentualComparecimento(),
                quorumMinimo,
                resultado.quorumAtingido(),
                totalPontos,
                apuracao.getTotalCedulas(),
                opcoes,
                homologacao,
                null,
                new OrcamentoResultadoInfo(
                        resultado.orcamentoTotal(),
                        resultado.orcamentoUtilizado(),
                        resultado.saldoRestante(),
                        totalPontos));
    }

    private ResultadoVotacaoResponse montarResultadoAnulada(Votacao votacao) {
        UUID votacaoId = votacao.getId();
        Optional<Apuracao> apuracaoOpt = apuracaoRepository.findById(votacaoId);

        StatusApuracao statusApuracao = null;
        Instant apuradaEm = null;
        long totalParticipantes;
        long totalEleitoresAptos;
        BigDecimal percentualComparecimento;
        BigDecimal quorumMinimo;
        boolean quorumAtingido;
        long totalCedulas;

        if (apuracaoOpt.isPresent() && apuracaoOpt.get().getResultado() != null) {
            Apuracao apuracao = apuracaoOpt.get();
            ResultadoApuracao resultado = apuracao.getResultado();
            statusApuracao = resultado.status();
            apuradaEm = apuracao.getConcluidaEm();
            totalParticipantes = resultado.totalParticipantes();
            totalEleitoresAptos = resultado.totalEleitoresAptos();
            percentualComparecimento = resultado.percentualComparecimento();
            quorumMinimo = quorumDeSnapshot(apuracao, votacao);
            quorumAtingido = resultado.quorumAtingido();
            totalCedulas = apuracao.getTotalCedulas();
        } else if (apuracaoOpt.isPresent() && apuracaoOpt.get().getResultadoOrcamento() != null) {
            Apuracao apuracao = apuracaoOpt.get();
            ResultadoApuracaoOrcamento resultado = apuracao.getResultadoOrcamento();
            statusApuracao = resultado.status();
            apuradaEm = apuracao.getConcluidaEm();
            totalParticipantes = resultado.totalParticipantes();
            totalEleitoresAptos = resultado.totalEleitoresAptos();
            percentualComparecimento = resultado.percentualComparecimento();
            quorumMinimo = quorumDeSnapshot(apuracao, votacao);
            quorumAtingido = resultado.quorumAtingido();
            totalCedulas = apuracao.getTotalCedulas();
        } else {
            totalParticipantes = participacaoRepository.countByVotacaoId(votacaoId);
            totalEleitoresAptos =
                    papelVotacaoRepository.countByIdVotacaoIdAndIdPapel(
                            votacaoId, PapelUsuario.ELEITOR);
            percentualComparecimento =
                    totalEleitoresAptos == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(totalParticipantes)
                                    .multiply(CEM)
                                    .divide(
                                            BigDecimal.valueOf(totalEleitoresAptos),
                                            2,
                                            RoundingMode.HALF_UP);
            quorumMinimo = quorumDe(votacao.getRegras());
            quorumAtingido =
                    quorumMinimo == null
                            || (totalEleitoresAptos > 0
                                    && percentualComparecimento.compareTo(quorumMinimo) >= 0);
            totalCedulas = totalParticipantes;
        }

        AnulacaoInfo anulacao =
                construirAnulacao(
                        ultimaTransicaoPara(votacaoId, EstadoVotacao.Anulada).orElse(null),
                        totalParticipantes,
                        totalEleitoresAptos,
                        quorumMinimo);

        return new ResultadoVotacaoResponse(
                votacaoId,
                votacao.getNome(),
                votacao.getTipo(),
                votacao.getEstado(),
                statusApuracao,
                apuradaEm,
                null,
                null,
                List.of(),
                null,
                totalParticipantes,
                totalEleitoresAptos,
                percentualComparecimento,
                quorumMinimo,
                quorumAtingido,
                null,
                totalCedulas,
                List.of(),
                null,
                anulacao,
                null);
    }

    private static BigDecimal quorumDeSnapshot(Apuracao apuracao, Votacao votacao) {
        return apuracao.getRegra() != null
                ? apuracao.getRegra().quorumMinimoPercentual()
                : quorumDe(votacao.getRegras());
    }

    private static BigDecimal quorumDe(RegraVotacao regras) {
        return regras == null ? null : regras.quorumMinimoPercentual();
    }

    private HomologacaoInfo buscarHomologacao(UUID votacaoId) {
        return ultimaTransicaoPara(votacaoId, EstadoVotacao.Homologada)
                .map(t -> new HomologacaoInfo(t.getAutorId(), t.getOcorridaEm(), t.getMotivo()))
                .orElse(null);
    }

    private AnulacaoInfo construirAnulacao(
            TransicaoVotacao transicao,
            long totalParticipantes,
            long totalEleitoresAptos,
            BigDecimal quorumMinimo) {
        if (transicao == null) {
            return new AnulacaoInfo(OrigemTransicao.SISTEMA, null, null, null, "Votação anulada.");
        }
        String resumo;
        if (transicao.getOrigem() == OrigemTransicao.SISTEMA) {
            resumo =
                    quorumMinimo == null
                            ? "Anulada por falta de quórum: %d de %d eleitores"
                                    .formatted(totalParticipantes, totalEleitoresAptos)
                            : "Anulada por falta de quórum: %d de %d eleitores (%s%% exigido)"
                                    .formatted(
                                            totalParticipantes,
                                            totalEleitoresAptos,
                                            quorumMinimo.stripTrailingZeros().toPlainString());
        } else {
            resumo = "Anulada por irregularidade: " + transicao.getMotivo();
        }
        return new AnulacaoInfo(
                transicao.getOrigem(),
                transicao.getAutorId(),
                transicao.getOcorridaEm(),
                transicao.getMotivo(),
                resumo);
    }

    private Optional<TransicaoVotacao> ultimaTransicaoPara(UUID votacaoId, EstadoVotacao destino) {
        return transicaoVotacaoRepository.findByVotacaoIdOrderByOcorridaEmAsc(votacaoId).stream()
                .filter(t -> t.getEstadoNovo() == destino)
                .reduce((primeira, segunda) -> segunda);
    }
}
