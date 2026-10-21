package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoEleito;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.RodadaSobra;
import java.util.List;

/**
 * Conteúdo didático sobre o quociente eleitoral (RF-32, RNF-07): seções curtas de texto, um exemplo
 * numérico calculado passo a passo e a base legal com links oficiais.
 *
 * <p>Os records aninhados vivem aqui porque só fazem sentido como partes desta resposta.
 */
public record ExplicacaoQuocienteEleitoralResponse(
        String titulo,
        String resumo,
        List<SecaoDidatica> secoes,
        Exemplo exemplo,
        List<FonteLegal> baseLegal) {

    /** Seção curta; cada parágrafo cabe em até 5 linhas de leitura (RNF-07). */
    public record SecaoDidatica(String titulo, List<String> paragrafos) {}

    public record FonteLegal(String norma, String dispositivos, String url) {}

    public record Exemplo(
            int vagas,
            long votosValidos,
            long quocienteEleitoral,
            String calculoQuocienteEleitoral,
            long minimoVotosCandidatoQuociente,
            long minimoVotosPartidoSobras,
            long minimoVotosCandidatoSobras,
            List<PartidoExemplo> partidos,
            List<RodadaSobra> rodadasSobras,
            List<CandidatoEleito> eleitos,
            CasoContraIntuitivo casoContraIntuitivo,
            List<String> passos) {}

    public record PartidoExemplo(
            String sigla,
            long votosLegenda,
            long votosNominais,
            long votosTotais,
            long quocientePartidario,
            String calculoQuocientePartidario,
            int cadeirasPorQuociente,
            int cadeirasPorSobras,
            int cadeirasTotais,
            boolean disputaSobras,
            List<CandidatoExemplo> candidatos) {}

    public record CandidatoExemplo(String nome, long votos, boolean eleito) {}

    /** Par que mostra o ponto central da história: menos votos eleito, mais votos de fora. */
    public record CasoContraIntuitivo(
            CandidatoResumo eleitoComMenosVotos,
            CandidatoResumo naoEleitoComMaisVotos,
            String explicacao) {}

    public record CandidatoResumo(String nome, String partido, long votos) {}
}
