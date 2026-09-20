package com.unicamp.engsoft.eleicao.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Operação válida sintaticamente, mas proibida pelas regras de negócio
 * (ex.: votar em votação encerrada, voto duplicado). Mapeada para 409.
 */
public class RegraDeNegocioException extends DomainException {

	public RegraDeNegocioException(String mensagem) {
		super(mensagem, HttpStatus.CONFLICT);
	}
}
