package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Janela de voto (RF-13/RF-14): {@code EmVotacao} é o único estado que aceita {@link
 * OperacaoVotacao#VOTAR}. Antes ({@code Publicada}), durante a suspensão ({@code Suspensa}) e
 * depois ({@code Apuracao} em diante) o voto é recusado.
 *
 * <p>Nenhuma operação de configuração vale aqui: mudar regras, candidatos ou eleitores com votos já
 * registrados alteraria a eleição no meio da contagem.
 */
public final class EmVotacaoComportamento implements EstadoVotacaoComportamento {

    @Override
    public EstadoVotacao estado() {
        return EstadoVotacao.EmVotacao;
    }

    @Override
    public Set<OperacaoVotacao> permitidas() {
        return EnumSet.of(OperacaoVotacao.VOTAR);
    }
}
