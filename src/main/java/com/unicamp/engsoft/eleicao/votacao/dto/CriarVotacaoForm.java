package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Formulário da tela de criação de votação (RF-10, #67).
 *
 * <p>Existe só porque o {@code <input type="datetime-local">} envia data e hora <em>sem fuso</em>,
 * enquanto {@link CriarVotacaoRequest} trabalha com {@link java.time.Instant}. Não carrega
 * validação própria: é convertido em {@link CriarVotacaoRequest} e validado com as anotações dele,
 * para que tela e API respondam com as mesmas mensagens.
 *
 * <p>É uma classe com setters, e não um record, de propósito (#158). Record é ligado pelo
 * construtor: se um único campo não converte (data digitada à mão num navegador sem seletor, por
 * exemplo), o Spring não consegue construir o objeto e o entrega nulo junto com o erro. A tela
 * então não tem de onde reler os outros campos e falha ao renderizar. Com setters, o campo que não
 * converte fica nulo e só ele recebe o erro; os demais voltam preenchidos.
 */
@Getter
@Setter
public class CriarVotacaoForm {

    private TipoVotacao tipo = TipoVotacao.ELEICAO_PRIVADA;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime inicioEm;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fimEm;

    private String nome;
    private String descricao;

    public static CriarVotacaoForm vazio() {
        return new CriarVotacaoForm();
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
