package com.unicamp.engsoft.eleicao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Segurança da API (RF-01/RF-03): autenticação por JWT, sem sessão.
 *
 * <p>Públicos apenas o cadastro, o login, a documentação e a leitura do conteúdo didático de
 * eleições públicas; todo o resto exige token.
 *
 * <p>O conteúdo didático ({@code GET /api/eleicoes-publicas/didatico/**}, RF-32) é público
 * <em>porque</em> é informativo, igual para todos e não toca dados de usuário nem de votação; o
 * público-alvo inclui eleitores leigos (RNF-07), e exigir cadastro só para ler uma explicação
 * afastaria justamente quem mais precisa dela. Só o {@code GET} é liberado.
 *
 * <p>O {@code csrf.disable()} é seguro <em>porque</em> o token viaja no header {@code
 * Authorization}: não há credencial que o navegador anexe sozinho. Se um dia o token for para um
 * cookie, o CSRF precisa voltar junto.
 *
 * <p>O token não carrega papéis: eles valem por votação (RF-03) e são checados contra {@code
 * papeis_votacao} em cada operação, via {@code @PreAuthorize} e o bean {@code autorizacaoVotacao}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher CONTEUDO_DIDATICO =
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/api/eleicoes-publicas/didatico/**");

    /**
     * No conteúdo didático o token é ignorado. Sem isso, um cliente que mande um token vencido em
     * toda requisição levaria 401 numa rota pública: o filtro do resource server rejeita o token
     * inválido antes de a regra {@code permitAll} ser avaliada.
     */
    private static BearerTokenResolver ignorandoTokenNoConteudoDidatico() {
        BearerTokenResolver padrao = new DefaultBearerTokenResolver();
        return request -> CONTEUDO_DIDATICO.matches(request) ? null : padrao.resolve(request);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST, "/api/usuarios", "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers(CONTEUDO_DIDATICO)
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.bearerTokenResolver(ignorandoTokenNoConteudoDidatico())
                                        .jwt(Customizer.withDefaults()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    BCryptPasswordEncoder PasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
