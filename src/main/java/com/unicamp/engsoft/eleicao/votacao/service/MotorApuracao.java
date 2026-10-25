package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.OpcaoApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.VotoApuravel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Motor de apuração de votos (RF-15, RNF-05).
 *
 * <p>Aplica as regras configuradas na {@link RegraVotacao} (quórum mínimo, peso de votos, tipo de
 * maioria e segundo turno) sobre os votos apuráveis recebidos e determina o desfecho da eleição.
 */
@Component
public class MotorApuracao {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal DOIS = BigDecimal.valueOf(2);

    /**
     * Apura o resultado de uma votação a partir das regras e dos votos fornecidos.
     *
     * @param regra Regras de votação configuradas.
     * @param votos Lista de votos registrados na votação.
     * @param todasOpcoes Todas as opções cadastradas (candidatos/chapas), permitindo incluir opções
     *     com zero votos no ranking. Se {@code null}, utiliza apenas as opções presentes nos votos.
     * @param totalEleitoresAptos Quantidade de eleitores aptos a votar.
     * @param totalParticipantes Quantidade de eleitores que compareceram à votação.
     * @return O {@link ResultadoApuracao} contendo status, ranking e eventuais
     *     vencedores/finalistas.
     */
    public ResultadoApuracao apurar(
            RegraVotacao regra,
            List<VotoApuravel> votos,
            Collection<UUID> todasOpcoes,
            long totalEleitoresAptos,
            long totalParticipantes) {
        if (regra == null) {
            throw new IllegalArgumentException("A regra de votação é obrigatória para a apuração");
        }

        List<VotoApuravel> votosValidos = votos == null ? List.of() : votos;
        BigDecimal comparecimento = calcularComparecimento(totalEleitoresAptos, totalParticipantes);
        boolean quorumAtingido = verificarQuorum(regra, comparecimento);

        List<OpcaoApurada> ranking = totalizarVotos(regra, votosValidos, todasOpcoes);
        BigDecimal totalVotosValidos =
                ranking.stream()
                        .map(OpcaoApurada::totalVotos)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!quorumAtingido) {
            return new ResultadoApuracao(
                    StatusApuracao.QUORUM_NAO_ATINGIDO,
                    null,
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    false);
        }

        if (ranking.isEmpty()) {
            return new ResultadoApuracao(
                    StatusApuracao.SEM_VENCEDOR,
                    null,
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    true);
        }

        if (totalVotosValidos.compareTo(BigDecimal.ZERO) == 0) {
            return new ResultadoApuracao(
                    StatusApuracao.EMPATE,
                    null,
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    true);
        }

        return avaliarMaioria(
                regra,
                ranking,
                totalVotosValidos,
                totalParticipantes,
                totalEleitoresAptos,
                comparecimento);
    }

    /** Sobrecarga conveniente quando não é necessário pré-cadastrar opções zeradas. */
    public ResultadoApuracao apurar(
            RegraVotacao regra,
            List<VotoApuravel> votos,
            long totalEleitoresAptos,
            long totalParticipantes) {
        return apurar(regra, votos, null, totalEleitoresAptos, totalParticipantes);
    }

    private BigDecimal calcularComparecimento(long aptos, long participantes) {
        if (aptos <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(participantes)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(aptos), 2, RoundingMode.HALF_UP);
    }

    private boolean verificarQuorum(RegraVotacao regra, BigDecimal comparecimento) {
        if (regra.quorumMinimoPercentual() == null) {
            return true;
        }
        return comparecimento.compareTo(regra.quorumMinimoPercentual()) >= 0;
    }

    private List<OpcaoApurada> totalizarVotos(
            RegraVotacao regra, List<VotoApuravel> votos, Collection<UUID> todasOpcoes, EstrategiaDesempate estrategiaDesempate) {
        Map<UUID, BigDecimal> contagem = new HashMap<>();

        if (todasOpcoes != null) {
            for (UUID opcaoId : todasOpcoes) {
                if (opcaoId != null) {
                    contagem.put(opcaoId, BigDecimal.ZERO);
                }
            }
        }

        for (VotoApuravel voto : votos) {
            if (voto == null || voto.opcaoId() == null) {
                continue;
            }
            BigDecimal peso =
                    regra.votoPonderado()
                            ? (voto.peso() != null ? voto.peso() : BigDecimal.ONE)
                            : BigDecimal.ONE;
            contagem.merge(voto.opcaoId(), peso, BigDecimal::add);
        }

        BigDecimal totalGeral = contagem.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OpcaoApurada> ranking = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entrada : contagem.entrySet()) {
            BigDecimal percentual = BigDecimal.ZERO;
            if (totalGeral.compareTo(BigDecimal.ZERO) > 0) {
                percentual =
                        entrada.getValue()
                                .multiply(CEM)
                                .divide(totalGeral, 2, RoundingMode.HALF_UP);
            }
            ranking.add(new OpcaoApurada(entrada.getKey(), entrada.getValue(), percentual));
        }

        ranking.sort((a, b) -> {

        int comparacaoVotos = b.totalVotos().compareTo(a.totalVotos());
        
        if (comparacaoVotos == 0 && estrategiaDesempate != null) {
            return estrategiaDesempate.compare(a, b);
        }
            return comparacaoVotos;
    });
        return ranking;
    }

    private ResultadoApuracao avaliarMaioria(
            RegraVotacao regra,
            List<OpcaoApurada> ranking,
            BigDecimal totalVotosValidos,
            long totalParticipantes,
            long totalEleitoresAptos,
            BigDecimal comparecimento) {
        OpcaoApurada primeiro = ranking.get(0);
        OpcaoApurada segundo = ranking.size() > 1 ? ranking.get(1) : null;

        if (regra.tipoMaioria() == TipoMaioria.MAIORIA_SIMPLES) {
            if (segundo != null && primeiro.totalVotos().compareTo(segundo.totalVotos()) == 0) {
                return new ResultadoApuracao(
                        StatusApuracao.EMPATE,
                        null,
                        List.of(),
                        ranking,
                        totalVotosValidos,
                        totalParticipantes,
                        totalEleitoresAptos,
                        comparecimento,
                        true);
            }
            return new ResultadoApuracao(
                    StatusApuracao.DEFINIDO,
                    primeiro.opcaoId(),
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    true);
        }

        boolean atingiuCriterio =
                regra.tipoMaioria() == TipoMaioria.MAIORIA_ABSOLUTA
                        ? atingiuMaioriaAbsoluta(primeiro.totalVotos(), totalVotosValidos)
                        : atingiuMaioriaQualificada(
                                primeiro.totalVotos(),
                                totalVotosValidos,
                                regra.percentualQualificado());

        if (atingiuCriterio) {
            return new ResultadoApuracao(
                    StatusApuracao.DEFINIDO,
                    primeiro.opcaoId(),
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    true);
        }

        if (regra.segundoTurno()) {
            return definirSegundoTurno(
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento);
        }

        return new ResultadoApuracao(
                StatusApuracao.SEM_VENCEDOR,
                null,
                List.of(),
                ranking,
                totalVotosValidos,
                totalParticipantes,
                totalEleitoresAptos,
                comparecimento,
                true);
    }

    private boolean atingiuMaioriaAbsoluta(BigDecimal votos, BigDecimal totalValidos) {
        return votos.multiply(DOIS).compareTo(totalValidos) > 0;
    }

    private boolean atingiuMaioriaQualificada(
            BigDecimal votos, BigDecimal totalValidos, BigDecimal percentualQualificado) {
        return votos.multiply(CEM).compareTo(totalValidos.multiply(percentualQualificado)) >= 0;
    }

    private ResultadoApuracao definirSegundoTurno(
            List<OpcaoApurada> ranking,
            BigDecimal totalVotosValidos,
            long totalParticipantes,
            long totalEleitoresAptos,
            BigDecimal comparecimento) {
        if (ranking.size() < 2) {
            return new ResultadoApuracao(
                    StatusApuracao.SEM_VENCEDOR,
                    null,
                    List.of(),
                    ranking,
                    totalVotosValidos,
                    totalParticipantes,
                    totalEleitoresAptos,
                    comparecimento,
                    true);
        }

        OpcaoApurada primeiro = ranking.get(0);
        OpcaoApurada segundo = ranking.get(1);

        if (ranking.size() > 2) {
            OpcaoApurada terceiro = ranking.get(2);
            if (segundo.totalVotos().compareTo(terceiro.totalVotos()) == 0) {
                return new ResultadoApuracao(
                        StatusApuracao.EMPATE,
                        null,
                        List.of(),
                        ranking,
                        totalVotosValidos,
                        totalParticipantes,
                        totalEleitoresAptos,
                        comparecimento,
                        true);
            }
        }

        return new ResultadoApuracao(
                StatusApuracao.SEGUNDO_TURNO,
                null,
                List.of(primeiro.opcaoId(), segundo.opcaoId()),
                ranking,
                totalVotosValidos,
                totalParticipantes,
                totalEleitoresAptos,
                comparecimento,
                true);
    }
}
