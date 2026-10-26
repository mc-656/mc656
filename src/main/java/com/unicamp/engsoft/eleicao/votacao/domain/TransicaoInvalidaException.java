package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Máquina de estados viola condição já estabelecida. Mapeada para 409: a requisição é bem formada,
 * mas o estado atual da votação proíbe a operação, no mesmo molde da {@code
 * RegraDeNegocioException}.
 */
public class TransicaoInvalidaException extends DomainException {

    private final UUID votacaoId;
    private final EstadoVotacao estadoAtual;
    private final EstadoVotacao estadoDestino;

    public TransicaoInvalidaException(String mensagem) {
        super(mensagem, HttpStatus.CONFLICT);
        this.votacaoId = null;
        this.estadoAtual = null;
        this.estadoDestino = null;
    }

    public TransicaoInvalidaException(
            UUID votacaoId, EstadoVotacao estadoAtual, EstadoVotacao estadoDestino, String motivo) {
        super(
                String.format(
                        "Transição inválida para a votação %s: de %s para %s. Motivo: %s",
                        votacaoId, estadoAtual, estadoDestino, motivo),
                HttpStatus.CONFLICT);
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
