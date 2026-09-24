package com.unicamp.engsoft.eleicao.shared.exception;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handler global de exceptions. Traduz falhas em respostas ProblemDetail (RFC 7807), garantindo
 * formato de erro único em toda a API.
 *
 * <p>Só alcança exceptions lançadas a partir do controller. Falhas de autenticação/autorização do
 * Spring Security ocorrem antes, na cadeia de filtros, e são tratadas pela configuração de
 * segurança. A exceção é o login: como ele chama o {@code AuthenticationManager} de dentro do
 * controller, a falha de credencial sobe por aqui.
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que as exceptions do próprio Spring MVC
 * (rota inexistente, método não suportado, corpo ilegível) mantenham o status correto. Sem isso
 * elas caem na rede de segurança abaixo e todo 404 da API vira 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problema.setTitle(ex.getStatus().getReasonPhrase());
        return problema;
    }

    /**
     * Sobrescreve em vez de declarar um {@code @ExceptionHandler} próprio: a superclasse já mapeia
     * esta exception, e dois handlers para o mesmo tipo quebram a aplicação no startup.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setTitle("Requisição inválida");
        problema.setDetail("Um ou mais campos falharam na validação.");
        problema.setProperty(
                "erros",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
                        .toList());
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Credenciais inválidas no login.
     *
     * <p>Sem este handler a falha cairia na rede de segurança abaixo e senha errada viraria 500.
     *
     * <p>A mensagem é a mesma para e-mail inexistente e senha incorreta, de propósito: distinguir
     * os dois casos revela quais contas existem.
     */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAutenticacao(AuthenticationException ex) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problema.setTitle("Não autenticado");
        problema.setDetail("E-mail ou senha inválidos.");
        return problema;
    }

    /**
     * Rede de segurança: qualquer falha não prevista vira 500 sem vazar detalhes internos na
     * resposta. O diagnóstico fica no log.
     *
     * <p>Só alcança o que a superclasse não mapeou: handlers de tipo mais específico têm
     * precedência sobre este.
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
