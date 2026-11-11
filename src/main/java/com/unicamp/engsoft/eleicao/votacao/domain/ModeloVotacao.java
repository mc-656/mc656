package com.unicamp.engsoft.eleicao.votacao.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modelos prontos de votação (RF-16): cada um fixa uma {@link RegraVotacao} para que o organizador
 * crie e publique uma votação sem escolher regras — 71,4% dos respondentes da elicitação não
 * pretendem configurar votações (decisão D6 da issue #77).
 *
 * <p>É um enum em código, e não uma tabela, porque os modelos são parte do produto: mudar um deles
 * é decisão de design revisada em PR, não dado editável em produção. Também por isso a votação não
 * guarda de qual modelo veio — depois de criada, ela é uma votação comum, com regras editáveis em
 * rascunho pelo fluxo de RF-11.
 *
 * <p>Todos os modelos são de voto secreto: o sigilo é garantido pela plataforma para qualquer
 * votação (RNF-02), não é uma opção de regra. O atributo {@link #votoSecreto()} existe só para a
 * tela de criação exibir essa característica, que o critério de aceite destaca na eleição de chapa.
 */
public enum ModeloVotacao {
    ELEICAO_CHAPA(
            "Eleição de chapa",
            "Escolha entre chapas ou candidatos; vence a mais votada, com voto secreto.",
            new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false),
            List.of()),

    ASSEMBLEIA(
            "Assembleia",
            "Deliberação de assembleia; vence a opção mais votada se ao menos 50% dos aptos votarem.",
            new RegraVotacao(
                    TipoMaioria.MAIORIA_SIMPLES, null, false, BigDecimal.valueOf(50), false),
            List.of()),

    DECISAO_SIM_NAO(
            "Decisão sim/não",
            "Pergunta com respostas Sim e Não; vence a resposta mais votada.",
            new RegraVotacao(TipoMaioria.MAIORIA_SIMPLES, null, false, null, false),
            List.of("Sim", "Não"));

    private final String nome;
    private final String descricao;
    private final RegraVotacao regra;
    private final List<String> opcoesPadrao;

    ModeloVotacao(String nome, String descricao, RegraVotacao regra, List<String> opcoesPadrao) {
        this.nome = nome;
        this.descricao = descricao;
        this.regra = regra;
        this.opcoesPadrao = opcoesPadrao;
    }

    public String nome() {
        return nome;
    }

    /** Descrição curta (até 100 caracteres), usada também como descrição padrão da votação. */
    public String descricao() {
        return descricao;
    }

    /**
     * Regra aplicada à votação criada; {@link RegraVotacao} é imutável, então pode ser
     * compartilhada.
     */
    public RegraVotacao regra() {
        return regra;
    }

    /** Opções cadastradas quando o organizador não informa nenhuma; vazia se não houver padrão. */
    public List<String> opcoesPadrao() {
        return opcoesPadrao;
    }

    public boolean votoSecreto() {
        return true;
    }
}
