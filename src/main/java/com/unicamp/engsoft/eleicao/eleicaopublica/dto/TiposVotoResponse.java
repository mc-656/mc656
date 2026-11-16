package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import java.util.List;

/**
 * Conteúdo didático sobre voto nulo, em branco e na legenda (RF-32, RNF-07, #91).
 *
 * <p>Os records aninhados vivem aqui porque só fazem sentido como partes desta resposta. O mesmo
 * conteúdo alimenta a página {@code /eleicoes-publicas/tipos-voto} e a API JSON, para que as duas
 * nunca divirjam.
 */
public record TiposVotoResponse(
        String titulo,
        String resumo,
        List<TipoVoto> tipos,
        Secao legendaNoQuociente,
        String urlExplicacaoQuociente,
        List<Fonte> fontes) {

    /**
     * Um tipo de voto. {@code id} é estável e serve de âncora na página (ex.: {@code
     * /eleicoes-publicas/tipos-voto#legenda}).
     */
    public record TipoVoto(
            String id,
            String nome,
            String oQueE,
            String comoRegistrarNaUrna,
            boolean contaComoValido,
            String efeitoNoResultado) {}

    public record Secao(String titulo, List<String> paragrafos) {}

    public record Fonte(String descricao, String url) {}
}
