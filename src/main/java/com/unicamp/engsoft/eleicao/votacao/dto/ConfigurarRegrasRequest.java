package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Payload de configuração das regras de apuração (RF-11). A validação de combinação (percentual só
 * na maioria qualificada, segundo turno sem maioria simples etc.) é do value object {@link
 * RegraVotacao}, devolvida como 400.
 *
 * <p>Os booleans são {@link Boolean} e não {@code boolean}: o Jackson rejeita {@code null} em
 * primitivo quando o campo vem ausente do JSON, e aqui ausente significa {@code false}.
 */
public record ConfigurarRegrasRequest(
        @NotNull(message = "O tipo de maioria é obrigatório") TipoMaioria tipoMaioria,
        BigDecimal percentualQualificado,
        Boolean segundoTurno,
        BigDecimal quorumMinimoPercentual,
        Boolean votoPonderado) {

    public RegraVotacao paraRegra() {
        return new RegraVotacao(
                tipoMaioria,
                percentualQualificado,
                Boolean.TRUE.equals(segundoTurno),
                quorumMinimoPercentual,
                Boolean.TRUE.equals(votoPonderado));
    }
}
