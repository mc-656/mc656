package com.unicamp.engsoft.eleicao.votacao.domain;

/**
 * Estados de uma {@link Votacao} (SPECS §4.2), persistidos por nome ({@code @Enumerated(STRING)}).
 *
 * <p>O enum é <b>só a chave de persistência</b>: ele não guarda regra nenhuma. O que cada estado
 * permite fazer (padrão State) mora em {@link EstadoVotacaoComportamento} e é consultado via {@link
 * ComportamentosDeEstado}; as transições entre estados ficam centralizadas no {@code
 * VotacaoStateService} (SPECS §7.3), com auditoria (RNF-03).
 *
 * <p>Por isso não vira uma hierarquia de classes: trocar o enum quebraria o {@code VARCHAR(30)} da
 * coluna {@code estado} e exigiria migração de todo o histórico auditado.
 */
public enum EstadoVotacao {
    RascunhoVotacao,
    Publicada,
    EmVotacao,
    Suspensa,
    Apuracao,
    Encerrada,
    Homologada,
    Anulada,
    Cancelada
}
