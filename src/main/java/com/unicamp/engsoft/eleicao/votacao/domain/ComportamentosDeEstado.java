package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry {@code EstadoVotacao → comportamento} (padrão State, SPECS §4.2): o único lugar que sabe
 * o que pode acontecer em cada estado.
 *
 * <p>O enum continua sendo a chave de persistência ({@code @Enumerated(STRING)} em {@code
 * VARCHAR(30)}); o comportamento é pendurado aqui, sem hierarquia de classes no lugar do enum e sem
 * migração de schema. Registro por valor, então todo estado novo já nasce com o fallback {@link
 * SemOperacoes} e {@link #de} nunca devolve {@code null}.
 */
public final class ComportamentosDeEstado {

    private static final Map<EstadoVotacao, EstadoVotacaoComportamento> POR_ESTADO =
            new EnumMap<>(EstadoVotacao.class);

    static {
        for (EstadoVotacao estado : EstadoVotacao.values()) {
            POR_ESTADO.put(estado, new SemOperacoes(estado));
        }
        // Estados com comportamento próprio sobrescrevem o fallback.
        POR_ESTADO.put(EstadoVotacao.RascunhoVotacao, new RascunhoVotacaoComportamento());
    }

    private ComportamentosDeEstado() {}

    /** Comportamento do estado, para o service consultar em vez de reimplementar a regra. */
    public static EstadoVotacaoComportamento de(EstadoVotacao estado) {
        EstadoVotacaoComportamento comportamento = POR_ESTADO.get(estado);
        if (comportamento == null) {
            // Não acontece com o registro por valor; degrada para erro de programação, não 409.
            throw new IllegalStateException("Sem comportamento cadastrado para o estado " + estado);
        }
        return comportamento;
    }

    /** Estados que permitem a operação, consultando cada comportamento — a fonte única da regra. */
    public static Set<EstadoVotacao> estadosQuePermitem(OperacaoVotacao operacao) {
        return POR_ESTADO.values().stream()
                .filter(comportamento -> comportamento.permite(operacao))
                .map(EstadoVotacaoComportamento::estado)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EstadoVotacao.class)));
    }

    /**
     * Mensagem única da API para uma operação negada: diz qual estado é o exigido e em qual a
     * votação está. Fica aqui, e não em cada estado, porque depende dos estados que <b>permitem</b>
     * a operação — informação que só o registry tem.
     */
    public static String mensagemDeNegativa(EstadoVotacao estadoAtual, OperacaoVotacao operacao) {
        Set<EstadoVotacao> permitidos = estadosQuePermitem(operacao);
        if (permitidos.isEmpty()) {
            return "A operação '%s' não é permitida em nenhum estado (estado atual: %s)"
                    .formatted(operacao.rotulo(), estadoAtual);
        }
        return "A operação '%s' só pode ser feita com a votação em %s (estado atual: %s)"
                .formatted(operacao.rotulo(), String.join(", ", nomes(permitidos)), estadoAtual);
    }

    private static List<String> nomes(Set<EstadoVotacao> estados) {
        return estados.stream().map(EstadoVotacao::name).toList();
    }
}
