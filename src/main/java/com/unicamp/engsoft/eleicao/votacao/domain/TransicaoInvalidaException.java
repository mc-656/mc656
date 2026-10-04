package com.unicamp.engsoft.eleicao.votacao.domain;

import java.util.UUID;

/**
Maquina de estados viola condicao ja estabelecida
 */
public class TransicaoInvalidaException extends RuntimeException {

    private final UUID votacaoId;
    private final EstadoVotacao estadoAtual;
    private final EstadoVotacao estadoDestino;

    public TransicaoInvalidaException(String mensagem) {
        super(mensagem);
        this.votacaoId = null;
        this.estadoAtual = null;
        this.estadoDestino = null;
    }

    public TransicaoInvalidaException(UUID votacaoId, EstadoVotacao estadoAtual, EstadoVotacao estadoDestino, String motivo) {
        super(String.format("Transição inválida para a votação %s: de %s para %s. Motivo: %s", 
                votacaoId, estadoAtual, estadoDestino, motivo));
        this.votacaoId = votacaoId;
        this.estadoAtual = estadoAtual;
        this.estadoDestino = estadoDestino;
    }

    public UUID getVotacaoId() {
        return votacaoId;
    }

    public EstadoVotacao getEstadoAtual() {
        return estadoAtual;
    }

    public EstadoVotacao getEstadoDestino() {
        return estadoDestino;
    }
}