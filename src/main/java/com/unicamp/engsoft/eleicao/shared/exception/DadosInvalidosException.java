package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Dados que violam uma invariante do domínio (ex.: combinação inválida de regras de votação, peso
 * negativo). Mapeada para 400.
 */
public class DadosInvalidosException extends DomainException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem, HttpStatus.BAD_REQUEST);
    }
}
