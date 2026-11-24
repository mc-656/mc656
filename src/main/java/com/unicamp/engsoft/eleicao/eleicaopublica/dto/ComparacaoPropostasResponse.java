package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import java.util.List;
import java.util.UUID;

/** Comparação lado a lado de propostas de 2 a 4 candidatos ao mesmo cargo (RF-31, #89). */
public record ComparacaoPropostasResponse(
        EleicaoPublicaResponse eleicao,
        UUID cargoId,
        String cargoNome,
        List<CandidatoComparado> candidatos,
        List<LinhaTema> temas) {

    public ComparacaoPropostasResponse {
        candidatos = candidatos == null ? List.of() : List.copyOf(candidatos);
        temas = temas == null ? List.of() : List.copyOf(temas);
    }

    public record CandidatoComparado(
            UUID id,
            String nomeUrna,
            String numero,
            String partidoSigla,
            String partidoNome,
            FonteResponse fonte) {}

    public record LinhaTema(String tema, List<PropostasDoCandidato> porCandidato) {
        public LinhaTema {
            porCandidato = porCandidato == null ? List.of() : List.copyOf(porCandidato);
        }
    }

    public record PropostasDoCandidato(UUID candidatoId, List<PropostaItem> propostas) {
        public PropostasDoCandidato {
            propostas = propostas == null ? List.of() : List.copyOf(propostas);
        }
    }

    public record PropostaItem(UUID id, String titulo, String descricao, FonteResponse fonte) {}
}
