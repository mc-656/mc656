package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Usuário autenticado sem permissão para a operação (ex.: alterar votação de outro administrador).
 * Mapeada para 403.
 */
public class AcessoNegadoException extends DomainException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem, HttpStatus.FORBIDDEN);
    }
}
