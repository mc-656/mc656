package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Comprovante de voto que não passou na verificação (RNF-01): adulterado, emitido com outra chave
 * ou simplesmente malformado. Mapeada para 400.
 *
 * <p>A mensagem é deliberadamente a mesma para todos os casos: distinguir "formato errado" de "tag
 * inválida" só ajudaria quem está tentando forjar um comprovante.
 */
public class ComprovanteInvalidoException extends DomainException {

    private static final String MENSAGEM = "Comprovante de voto inválido ou adulterado.";

    public ComprovanteInvalidoException() {
        super(MENSAGEM, HttpStatus.BAD_REQUEST);
    }

    public ComprovanteInvalidoException(Throwable causa) {
        super(MENSAGEM, HttpStatus.BAD_REQUEST, causa);
    }
}
