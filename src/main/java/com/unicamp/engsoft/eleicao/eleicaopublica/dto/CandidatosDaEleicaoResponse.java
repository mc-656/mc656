package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoPublico;
import java.util.List;
import java.util.UUID;

/**
 * Candidatos de uma eleição pública agrupados por cargo, na ordem da urna (RF-30/RF-31). Cargos sem
 * candidato registrado aparecem com lista vazia, para o eleitor saber que o cargo existe.
 */
public record CandidatosDaEleicaoResponse(EleicaoPublicaResponse eleicao, List<Cargo> cargos) {

    public record Cargo(UUID id, String nome, List<Candidato> candidatos) {}

    public record Candidato(
            UUID id,
            String nomeUrna,
            String numero,
            String partidoSigla,
            String partidoNome,
            FonteResponse fonte) {

        public static Candidato de(CandidatoPublico candidato) {
            return new Candidato(
                    candidato.getId(),
                    candidato.getNomeUrna(),
                    candidato.getNumero(),
                    candidato.getPartidoSigla(),
                    candidato.getPartidoNome(),
                    FonteResponse.de(candidato.getFonte()));
        }
    }
}
