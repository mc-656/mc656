package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/** Recurso solicitado não existe. Mapeada para 404. */
public class RecursoNaoEncontradoException extends DomainException {

    public RecursoNaoEncontradoException(String recurso, Object identificador) {
        super("%s não encontrado(a): %s".formatted(recurso, identificador), HttpStatus.NOT_FOUND);
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem, HttpStatus.NOT_FOUND);
    }
}
