package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Usuário autenticado não é elegível para votar na votação (RF-02). Mapeada para 403.
 *
 * <p>Tipo próprio, em vez da {@code AcessoNegadoException} genérica, para que quem consome a
 * validação (registro de voto) e os testes identifiquem o motivo da recusa sem depender da
 * mensagem.
 */
public class UsuarioNaoElegivelException extends DomainException {

    private final UUID votacaoId;

    public UsuarioNaoElegivelException(UUID votacaoId) {
        super(
                "Usuário não é elegível para votar na votação %s.".formatted(votacaoId),
                HttpStatus.FORBIDDEN);
        this.votacaoId = votacaoId;
    }

    public UUID getVotacaoId() {
        return votacaoId;
    }
}
