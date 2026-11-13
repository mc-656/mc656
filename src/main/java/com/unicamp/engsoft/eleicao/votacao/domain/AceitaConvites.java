package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Comportamento de {@code Publicada} e {@code EmVotacao}: os únicos estados em que o convite de
 * eleitores funciona (RF-17).
 *
 * <p>O convite só existe depois da publicação porque é aí que a votação fica fechada (regras,
 * candidatos) e pode ser divulgada. {@code Publicada} também aceita para que o organizador consiga
 * reunir o colégio eleitoral antes da abertura, que é quando o link mais circula. Fora de {@code
 * EmVotacao} (suspensa, em apuração, encerrada...) o link deixa de incluir: um eleitor que entrasse
 * depois do fim mudaria o denominador do quórum de uma votação que já não aceita votos.
 *
 * <p>As operações de configuração continuam exclusivas do rascunho: entrar por convite não é
 * "gerenciar eleitores" — não há escolha de peso, e o organizador não escolhe quem entra além da
 * lista do modo restrito.
 */
public record AceitaConvites(EstadoVotacao estado) implements EstadoVotacaoComportamento {

    @Override
    public Set<OperacaoVotacao> permitidas() {
        return EnumSet.of(OperacaoVotacao.CONVIDAR_ELEITORES, OperacaoVotacao.ENTRAR_POR_CONVITE);
    }
}
