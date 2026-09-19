package com.unicamp.engsoft.eleicao.shared.exception;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler global de exceptions. Traduz falhas em respostas ProblemDetail
 * (RFC 7807), garantindo formato de erro único em toda a API.
 *
 * <p>Só alcança exceptions lançadas a partir do controller. Falhas de
 * autenticação/autorização do Spring Security ocorrem antes, na cadeia de
 * filtros, e são tratadas pela configuração de segurança.
 * 
 *
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(DomainException.class)
	ProblemDetail handleDomain(DomainException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problema.setTitle(ex.getStatus().getReasonPhrase());
		return problema;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidacao(MethodArgumentNotValidException ex) {
		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problema.setTitle("Requisição inválida");
		problema.setDetail("Um ou mais campos falharam na validação.");
		problema.setProperty("erros", ex.getBindingResult().getFieldErrors().stream()
				.map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
				.toList());
		return problema;
	}

	/**
	 * Rede de segurança: qualquer falha não prevista vira 500 sem vazar
	 * detalhes internos na resposta. O diagnóstico fica no log.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleInesperada(Exception ex) {
		log.error("Erro não tratado", ex);
		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		problema.setTitle("Erro interno");
		problema.setDetail("Erro inesperado ao processar a requisição.");
		problema.setType(URI.create("about:blank"));
		return problema;
	}
}
