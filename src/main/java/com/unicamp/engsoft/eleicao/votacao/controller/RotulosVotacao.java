package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Textos de exibição das views de votação (#67), usados nos templates como {@code
 * ${@rotulosVotacao.estado(v.estado)}}.
 *
 * <p>Os enums são chave de persistência e de API; dar a eles um rótulo em português acoplaria o
 * domínio à apresentação. O fuso também mora aqui porque é decisão de tela: o domínio guarda {@link
 * Instant}, e as telas mostram e recebem horário de Brasília, o do público do MVP.
 */
@Component("rotulosVotacao")
public class RotulosVotacao {

    /** Fuso em que as telas exibem e interpretam datas. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);

    public String dataHora(Instant instante) {
        return instante == null ? "" : DATA_HORA.format(instante);
    }

    public String tipo(TipoVotacao tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case ELEICAO_PRIVADA -> "Eleição privada";
            case ORCAMENTO_PARTICIPATIVO -> "Orçamento participativo";
        };
    }

    public String maioria(TipoMaioria maioria) {
        if (maioria == null) {
            return "";
        }
        return switch (maioria) {
            case MAIORIA_SIMPLES -> "Maioria simples";
            case MAIORIA_ABSOLUTA -> "Maioria absoluta";
            case MAIORIA_QUALIFICADA -> "Maioria qualificada";
        };
    }

    public String estado(EstadoVotacao estado) {
        if (estado == null) {
            return "";
        }
        return switch (estado) {
            case RascunhoVotacao -> "Rascunho";
            case Publicada -> "Publicada";
            case EmVotacao -> "Em votação";
            case Suspensa -> "Suspensa";
            case Apuracao -> "Em apuração";
            case Encerrada -> "Encerrada";
            case Homologada -> "Homologada";
            case Anulada -> "Anulada";
            case Cancelada -> "Cancelada";
        };
    }
}
