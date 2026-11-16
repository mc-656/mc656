package com.unicamp.engsoft.eleicao.shared.observabilidade;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Id de correlação por requisição (RNF-06): lê {@value #HEADER} do cliente ou gera um, põe no MDC
 * (e daí em toda linha de log da requisição) e devolve no header da resposta, para que um erro
 * relatado pelo usuário seja localizado no log.
 *
 * <p>Roda antes de tudo ({@link Ordered#HIGHEST_PRECEDENCE}), inclusive da segurança, para que
 * respostas 401/403 também saiam com o id. O valor recebido só é aceito se for curto e de
 * caracteres seguros: ele vai direto para o log, e um valor livre permitiria forjar linhas (log
 * injection) ou inflar o log. Fora do padrão, é descartado e substituído por um UUID.
 *
 * <p>O id identifica a requisição, não o usuário, e nenhum dado de voto é posto no MDC (RNF-02).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CHAVE_MDC = "correlationId";

    private static final Pattern FORMATO_ACEITO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object jaDefinido = request.getAttribute(CHAVE_MDC);
        String id = jaDefinido instanceof String s ? s : idDe(request.getHeader(HEADER));
        request.setAttribute(CHAVE_MDC, id);
        MDC.put(CHAVE_MDC, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }

    /**
     * Também no dispatch de erro ({@code /error}) e assíncrono, reaproveitando o id da requisição
     * original (atributo da requisição): é onde aparecem os logs de falhas não tratadas, os que
     * mais precisam do id.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /** Reaproveita o id recebido se estiver no formato seguro; caso contrário gera um novo. */
    static String idDe(String recebido) {
        if (recebido != null && FORMATO_ACEITO.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
