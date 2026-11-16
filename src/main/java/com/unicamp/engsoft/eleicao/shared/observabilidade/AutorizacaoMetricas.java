package com.unicamp.engsoft.eleicao.shared.observabilidade;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Quem pode ler {@code /actuator/prometheus} (RNF-06): só as contas listadas em {@code
 * observabilidade.metricas.emails-autorizados} (variável {@code METRICAS_EMAILS}, separada por
 * vírgula).
 *
 * <p>Estar autenticado não basta <em>porque</em> o cadastro é público: qualquer pessoa criaria uma
 * conta e leria volume de uso, falhas e rotas. O token não carrega papéis e os papéis do sistema
 * valem por votação (RF-03), então não há papel global de operador para checar; a lista na
 * configuração é o "perfil autorizado" da issue #138, sem migração nova. Compara-se o claim {@code
 * email}, assinado no JWT pelo {@code TokenService}, por ser o que o operador conhece. Lista vazia
 * (o padrão) nega a todos.
 */
@Component
public class AutorizacaoMetricas implements AuthorizationManager<RequestAuthorizationContext> {

    private final Set<String> emailsAutorizados;

    public AutorizacaoMetricas(
            @Value("${observabilidade.metricas.emails-autorizados:}") List<String> emails) {
        this.emailsAutorizados =
                emails.stream()
                        .map(String::trim)
                        .filter(e -> !e.isEmpty())
                        .map(e -> e.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext ctx) {

        Authentication auth = authentication.get();

        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        if (!(auth.getPrincipal() instanceof Jwt jwt)) {
            return new AuthorizationDecision(false);
        }

        String email = jwt.getClaimAsString("email");

        if (email == null) {
            return new AuthorizationDecision(false);
        }

        boolean permitido = emailsAutorizados.contains(email.toLowerCase(Locale.ROOT));

        return new AuthorizationDecision(permitido);
    }
}
