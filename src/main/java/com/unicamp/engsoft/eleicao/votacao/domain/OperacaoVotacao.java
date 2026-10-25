package com.unicamp.engsoft.eleicao.votacao.domain;

/**
 * Operações de escrita cuja permissão depende do estado da votação (padrão State, SPECS §4.2).
 *
 * <p>O rótulo existe só para a mensagem de erro unificada da API ("a operação 'gerenciar eleitores'
 * ..."): é a mesma regra para candidatos, eleitores e regras, e as três precisam responder igual.
 *
 * <p>Uma operação nova (votar, RF-14; propor, RF-20) entra aqui e é escolhida <b>explicitamente</b>
 * por cada estado que a permite, em seu {@link EstadoVotacaoComportamento#permitidas()}: nunca por
 * um {@code EnumSet.allOf}, senão a próxima operação estaria liberada de graça em estados errados.
 */
public enum OperacaoVotacao {
    CONFIGURAR_REGRAS("configurar as regras"),
    GERENCIAR_CANDIDATOS("gerenciar candidatos/chapas"),
    GERENCIAR_ELEITORES("gerenciar eleitores");

    private final String rotulo;

    OperacaoVotacao(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Nome legível da operação, usado nas mensagens de erro. */
    public String rotulo() {
        return rotulo;
    }
}
