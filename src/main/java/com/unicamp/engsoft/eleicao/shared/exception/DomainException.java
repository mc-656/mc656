package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de todas as exceptions de regra de negócio do projeto.
 *
 * <p>Cada subclasse carrega o status HTTP que a representa, de modo que o {@link
 * GlobalExceptionHandler} traduza a exception sem precisar conhecer cada caso individualmente.
 *
 * <p>Na prática, converte um erro de regra de negócios ou de recursos em semântica de erro HTTP.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }

    protected DomainException(String mensagem, HttpStatus status, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
