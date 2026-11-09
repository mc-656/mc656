package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Voto recusado porque a votação não está aberta (RF-14): ainda {@code Publicada}, {@code
 * Suspensa}, já encerrada ou com o prazo de fim vencido. Mapeada para 409.
 *
 * <p>Separada de {@link VotoDuplicadoException} de propósito (ver o Javadoc de lá). A mensagem traz
 * o estado atual para o eleitor saber se vale esperar (abertura, retomada) ou não.
 */
public class VotoForaDaJanelaException extends DomainException {

    public VotoForaDaJanelaException(UUID votacaoId, EstadoVotacao estadoAtual) {
        super(
                "Voto fora da janela: a votação %s não está aberta para votos (estado atual: %s)."
                        .formatted(votacaoId, estadoAtual),
                HttpStatus.CONFLICT);
    }
}
