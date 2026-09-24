package com.unicamp.engsoft.eleicao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança da API (RF-01/RF-03): autenticação por JWT, sem sessão.
 *
 * <p>Públicos apenas o cadastro, o login e a documentação; todo o resto exige token.
 *
 * <p>O {@code csrf.disable()} é seguro <em>porque</em> o token viaja no header {@code
 * Authorization}: não há credencial que o navegador anexe sozinho. Se um dia o token for para um
 * cookie, o CSRF precisa voltar junto.
 *
 * <p>TODO(M1): autorização por papel nos endpoints de votação, com {@code hasRole}.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationConverter conversor;

    SecurityConfig(JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.conversor = jwtAuthenticationConverter;
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
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)))
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
