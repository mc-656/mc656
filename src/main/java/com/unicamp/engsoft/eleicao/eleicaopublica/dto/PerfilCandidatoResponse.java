package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CargoAnterior;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.NoticiaCandidato;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.PropostaCandidato;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Perfil completo do candidato numa única resposta (RF-31/RF-33): a issue #87 nasce justamente da
 * dificuldade de juntar propostas, histórico e notícias espalhados em várias fontes. Cada item
 * carrega a própria {@link FonteResponse}, porque cada um pode vir de um lugar diferente.
 */
public record PerfilCandidatoResponse(
        UUID id,
        String nomeUrna,
        String nomeCompleto,
        String numero,
        String partidoSigla,
        String partidoNome,
        CargoDisputado cargoDisputado,
        FonteResponse fonte,
        List<CargoAnteriorResponse> cargosAnteriores,
        List<TemaPropostas> propostasPorTema,
        List<NoticiaResponse> noticias) {

    /**
     * {@code idDidatico} é o identificador do cargo no conteúdo didático (RF-32, ex.: {@code
     * deputado-federal}), usado para linkar a explicação do que o cargo faz em {@code
     * /eleicoes-publicas/cargos#<idDidatico>}.
     */
    public record CargoDisputado(
            UUID id, String nome, String idDidatico, UUID eleicaoId, String eleicaoNome) {}

    public record CargoAnteriorResponse(
            String cargo, int anoInicio, Integer anoFim, FonteResponse fonte) {

        public static CargoAnteriorResponse de(CargoAnterior cargo) {
            return new CargoAnteriorResponse(
                    cargo.getCargo(),
                    cargo.getAnoInicio(),
                    cargo.getAnoFim(),
                    FonteResponse.de(cargo.getFonte()));
        }
    }

    public record TemaPropostas(String tema, List<PropostaResponse> propostas) {}

    public record PropostaResponse(String titulo, String descricao, FonteResponse fonte) {

        public static PropostaResponse de(PropostaCandidato proposta) {
            return new PropostaResponse(
                    proposta.getTitulo(),
                    proposta.getDescricao(),
                    FonteResponse.de(proposta.getFonte()));
        }
    }

    public record NoticiaResponse(
            String titulo, String resumo, LocalDate publicadaEm, FonteResponse fonte) {

        public static NoticiaResponse de(NoticiaCandidato noticia) {
            return new NoticiaResponse(
                    noticia.getTitulo(),
                    noticia.getResumo(),
                    noticia.getPublicadaEm(),
                    FonteResponse.de(noticia.getFonte()));
        }
    }
}
