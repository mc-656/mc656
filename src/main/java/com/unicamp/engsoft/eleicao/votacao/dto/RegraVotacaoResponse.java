package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import java.math.BigDecimal;

/** Regras de apuração efetivamente gravadas, devolvidas para o administrador conferir. */
public record RegraVotacaoResponse(
        TipoMaioria tipoMaioria,
        BigDecimal percentualQualificado,
        boolean segundoTurno,
        BigDecimal quorumMinimoPercentual,
        boolean votoPonderado) {

    public static RegraVotacaoResponse of(RegraVotacao regra) {
        return new RegraVotacaoResponse(
                regra.tipoMaioria(),
                regra.percentualQualificado(),
                regra.segundoTurno(),
                regra.quorumMinimoPercentual(),
                regra.votoPonderado());
    }
}
