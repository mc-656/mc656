package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Visão oficial do resultado ou anulação da votação (RF-41, RF-42, #52, #84, #85). */
public record ResultadoVotacaoResponse(
        UUID votacaoId,
        String nomeVotacao,
        TipoVotacao tipo,
        EstadoVotacao estado,
        StatusApuracao statusApuracao,
        Instant apuradaEm,
        UUID vencedorId,
        String vencedorNome,
        List<UUID> finalistasSegundoTurno,
        UUID segundoTurnoId,
        long totalParticipantes,
        long totalEleitoresAptos,
        BigDecimal percentualComparecimento,
        BigDecimal quorumMinimoPercentual,
        boolean quorumAtingido,
        BigDecimal totalVotosValidos,
        long totalCedulas,
        List<OpcaoResultadoResponse> opcoes,
        HomologacaoInfo homologacao,
        AnulacaoInfo anulacao) {

    public ResultadoVotacaoResponse {
        finalistasSegundoTurno =
                finalistasSegundoTurno == null ? List.of() : List.copyOf(finalistasSegundoTurno);
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
    }

    public record OpcaoResultadoResponse(
            UUID opcaoId,
            String nome,
            BigDecimal totalVotos,
            BigDecimal percentualVotosValidos,
            boolean vencedora,
            boolean finalistaSegundoTurno) {}

    public record HomologacaoInfo(UUID homologadaPorId, Instant homologadaEm, String motivo) {}

    public record AnulacaoInfo(
            OrigemTransicao origem,
            UUID autorId,
            Instant anuladaEm,
            String justificativa,
            String resumo) {}
}
