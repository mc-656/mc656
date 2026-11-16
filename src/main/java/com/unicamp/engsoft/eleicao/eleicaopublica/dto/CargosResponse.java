package com.unicamp.engsoft.eleicao.eleicaopublica.dto;

import java.util.List;

/**
 * Conteúdo didático sobre os cargos em disputa e a formação de chapas (RF-32, RNF-07, #92).
 *
 * <p>Os records aninhados vivem aqui porque só fazem sentido como partes desta resposta. O mesmo
 * conteúdo alimenta a página {@code /eleicoes-publicas/cargos} e a API JSON.
 */
public record CargosResponse(
        String titulo, String resumo, List<Secao> secoes, List<Cargo> cargos, List<Fonte> fontes) {

    /**
     * Um cargo eletivo. {@code id} é estável: é a âncora da página e a chave que o perfil do
     * candidato usa para apontar para a descrição do cargo ({@code urlDescricao}). Renomear um
     * {@code id} quebra links já publicados.
     *
     * @param atribuicoes no máximo 3 tópicos curtos, para caber numa leitura rápida (RNF-07)
     */
    public record Cargo(
            String id,
            String nome,
            String esfera,
            Sistema sistema,
            String mandato,
            String composicaoChapa,
            List<String> atribuicoes,
            String urlDescricao) {}

    /** Como os eleitos de um cargo são definidos a partir dos votos. */
    public enum Sistema {
        MAJORITARIO,
        PROPORCIONAL
    }

    public record Secao(String id, String titulo, List<String> paragrafos) {}

    public record Fonte(String descricao, String url) {}
}
