package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Formulário da tela de criação de votação (RF-10, #67).
 *
 * <p>Existe só porque o {@code <input type="datetime-local">} envia data e hora <em>sem fuso</em>,
 * enquanto {@link CriarVotacaoRequest} trabalha com {@link java.time.Instant}. Não carrega
 * validação própria: é convertido em {@link CriarVotacaoRequest} e validado com as anotações dele,
 * para que tela e API respondam com as mesmas mensagens.
 */
public record CriarVotacaoForm(
        TipoVotacao tipo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicioEm,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fimEm,
        String nome,
        String descricao) {

    public static CriarVotacaoForm vazio() {
        return new CriarVotacaoForm(TipoVotacao.ELEICAO_PRIVADA, null, null, null, null);
    }

    /** Interpreta as datas no fuso informado; campos ausentes seguem nulos para a validação. */
    public CriarVotacaoRequest paraRequest(ZoneId fuso) {
        return new CriarVotacaoRequest(
                tipo,
                inicioEm == null ? null : inicioEm.atZone(fuso).toInstant(),
                fimEm == null ? null : fimEm.atZone(fuso).toInstant(),
                nome,
                descricao);
    }
}
