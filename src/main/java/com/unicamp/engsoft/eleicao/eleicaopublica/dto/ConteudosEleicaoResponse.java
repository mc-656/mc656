package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TipoConteudoPublico;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Notícias e resumos de debates paginados e filtráveis de uma eleição pública (RF-33, #133, #135).
 */
public record ConteudosEleicaoResponse(
        EleicaoPublicaResponse eleicao,
        List<ConteudoPublicoItemResponse> itens,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        List<String> temasDisponiveis,
        List<CandidatoFiltroResumo> candidatosDisponiveis) {

    public ConteudosEleicaoResponse {
        itens = itens == null ? List.of() : List.copyOf(itens);
        temasDisponiveis = temasDisponiveis == null ? List.of() : List.copyOf(temasDisponiveis);
        candidatosDisponiveis =
                candidatosDisponiveis == null ? List.of() : List.copyOf(candidatosDisponiveis);
    }

    public record ConteudoPublicoItemResponse(
            UUID id,
            TipoConteudoPublico tipo,
            String titulo,
            String tema,
            String resumo,
            LocalDate publicadaEm,
            List<UUID> candidatoIds,
            List<TemaDebateResponse> temasDebate,
            FonteResponse fonte) {

        public ConteudoPublicoItemResponse {
            candidatoIds = candidatoIds == null ? List.of() : List.copyOf(candidatoIds);
            temasDebate = temasDebate == null ? List.of() : List.copyOf(temasDebate);
        }
    }

    public record TemaDebateResponse(String tema, List<TopicoDebateResponse> topicos) {
        public TemaDebateResponse {
            topicos = topicos == null ? List.of() : List.copyOf(topicos);
        }
    }

    public record TopicoDebateResponse(int ordem, String resumo) {}

    public record CandidatoFiltroResumo(
            UUID id, String nomeUrna, String partidoSigla, String cargoNome) {}
}
