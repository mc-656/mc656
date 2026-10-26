package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Único estado que permite operações de escrita hoje: regras (RF-11), candidatos/chapas (RF-12) e
 * eleitores (RF-02/RF-10) só mudam antes da publicação — depois ela fica visível para os
 * participantes e qualquer alteração mudaria a eleição que eles viram (ou o denominador do quórum).
 *
 * <p>A lista é explícita, nunca {@code EnumSet.allOf}: liberar por exceção é o que garante que a
 * próxima operação ({@code REGISTRAR_VOTO}, {@code GERENCIAR_PROPOSTAS}) seja decisão consciente. A
 * mensagem de erro não é daqui: vem de {@link ComportamentosDeEstado#mensagemDeNegativa}, única
 * para todas as operações.
 */
public final class RascunhoVotacaoComportamento implements EstadoVotacaoComportamento {

    @Override
    public EstadoVotacao estado() {
        return EstadoVotacao.RascunhoVotacao;
    }

    @Override
    public Set<OperacaoVotacao> permitidas() {
        return EnumSet.of(
                OperacaoVotacao.CONFIGURAR_REGRAS,
                OperacaoVotacao.GERENCIAR_CANDIDATOS,
                OperacaoVotacao.GERENCIAR_ELEITORES);
    }
}
