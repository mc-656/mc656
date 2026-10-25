package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.Set;

/**
 * Estado sem nenhuma operação de escrita permitida — hoje todos fora do rascunho, inclusive os
 * finais ({@code Encerrada}, {@code Homologada}, {@code Anulada}, {@code Cancelada}).
 *
 * <p>É um record parametrizado pelo estado para não virar oito arquivos idênticos. Quando um estado
 * ganhar comportamento próprio (ex.: {@code EmVotacao} com o voto do RF-14), ele vira uma classe
 * própria e deixa de usar este fallback no {@link ComportamentosDeEstado}.
 */
public record SemOperacoes(EstadoVotacao estado) implements EstadoVotacaoComportamento {

    @Override
    public Set<OperacaoVotacao> permitidas() {
        return Set.of();
    }
}
