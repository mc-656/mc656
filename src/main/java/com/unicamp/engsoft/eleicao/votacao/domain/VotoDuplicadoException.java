package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Eleitor que já votou tentou votar de novo na mesma votação (RF-14). Mapeada para 409.
 *
 * <p>Tipo próprio, separado de {@link VotoForaDaJanelaException}, para que cliente e testes
 * distingam os dois motivos de recusa sem depender do texto: ambos são 409, mas pedem reações
 * diferentes (aqui não há o que fazer; fora da janela pode valer esperar a abertura ou a retomada).
 */
public class VotoDuplicadoException extends DomainException {

    public VotoDuplicadoException(UUID votacaoId) {
        super(
                "Voto duplicado: o eleitor já registrou voto na votação %s.".formatted(votacaoId),
                HttpStatus.CONFLICT);
    }
}
