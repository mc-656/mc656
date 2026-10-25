package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import java.util.Set;

/**
 * Padrão State: cada estado responde pelo que é permitido nele, em vez de os services
 * reimplementarem a regra "só se mexe em rascunho" três vezes (RF-11, RF-12, RF-10).
 *
 * <p>Contrato estreito de propósito: o estado só diz o que pode (e, por consequência, o que não
 * pode). A tabela de transições ({@code VotacaoStateServiceImpl.TRANSICOES}), a auditoria de cada
 * transição (RNF-03) e as guardas de publicação continuam onde o SPECS §7.3 manda — no service. Não
 * confundir os dois: isto é <b>capacidade</b>, aquilo é <b>transição</b>.
 *
 * <p>Os objetos ficam em {@link ComportamentosDeEstado}; o enum {@link EstadoVotacao} continua
 * sendo a chave de persistência ({@code @Enumerated(STRING)}), sem mudança de schema.
 */
public interface EstadoVotacaoComportamento {

    /** Estado a que este comportamento pertence. */
    EstadoVotacao estado();

    /** Operações que este estado permite. Conjunto vazio para estados sem nenhuma. */
    Set<OperacaoVotacao> permitidas();

    default boolean permite(OperacaoVotacao operacao) {
        return permitidas().contains(operacao);
    }

    /**
     * Garante que a operação é permitida; é o que os services chamam.
     *
     * @throws RegraDeNegocioException (409 na API) se o estado não permitir a operação.
     */
    default void exigir(OperacaoVotacao operacao) {
        if (!permite(operacao)) {
            throw new RegraDeNegocioException(mensagemDeErro(operacao));
        }
    }

    /**
     * Mensagem única da API para uma operação negada. A formulação vem do registry (qual estado é
     * que permite a operação), então todas as operações respondem com o mesmo texto e ele continua
     * certo quando um estado começa a negar algo que antes permitia.
     */
    default String mensagemDeErro(OperacaoVotacao operacao) {
        return ComportamentosDeEstado.mensagemDeNegativa(estado(), operacao);
    }
}
