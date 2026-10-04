package com.unicamp.engsoft.eleicao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança da aplicação (RF-01/RF-03), em duas cadeias independentes (SPECS §7.3).
 *
 * <p><b>API</b> ({@code /api/**}): JWT, sem sessão. Públicos apenas o cadastro e o login. O {@code
 * csrf.disable()} é seguro <em>porque</em> o token viaja no header {@code Authorization}: não há
 * credencial que o navegador anexe sozinho. Se um dia o token for para um cookie, o CSRF precisa
 * voltar junto.
 *
 * <p><b>Views</b> (todo o resto): Thymeleaf com sessão e form login. Aqui a credencial é o cookie
 * de sessão, que o navegador anexa sozinho, então o CSRF fica <em>ligado</em>. As duas cadeias usam
 * o mesmo {@code UserDetailsService} e o mesmo encoder: a mesma conta entra pelos dois caminhos.
 *
 * <p>Nenhuma das duas carrega papéis: eles valem por votação (RF-03) e são checados contra {@code
 * papeis_votacao} em cada operação, via {@code @PreAuthorize} e o bean {@code autorizacaoVotacao}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Ordem 1 com {@code securityMatcher}: sem isso a cadeia das views, que não restringe caminho,
     * capturaria {@code /api/**} e a API passaria a responder com redirecionamento para o login.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST, "/api/usuarios", "/api/auth/login")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    /**
     * {@code /error} é público para que uma falha numa página pública não vire redirecionamento
     * para o login.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/login", "/cadastro", "/error", "/css/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .usernameParameter("email")
                                        .passwordParameter("senha")
                                        .defaultSuccessUrl("/", true)
                                        .failureUrl("/login?erro"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?saiu"))
                .httpBasic(basic -> basic.disable())
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
