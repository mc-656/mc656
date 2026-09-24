package com.unicamp.engsoft.eleicao.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtConfig {

    private final SecretKeySpec chave;
    private final String emitter;

    public JwtConfig(
            @Value("${jwt.secret}") String secret, @Value("${jwt.emitter}") String emitter) {
        this.chave = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.emitter = emitter;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(emitter));
        return decoder;
    }

    /**
     * Traduz as claims do token validado em um {@code Authentication}.
     *
     * <p>Sem isto o Spring procuraria a claim {@code scope} e prefixaria cada valor com {@code
     * SCOPE_}; como {@code TokenService} escreve os papéis em {@code papeis} já no formato {@code
     * ROLE_*} (vindos de {@code UsuarioAutenticado}), basta apontar o nome da claim e zerar o
     * prefixo — assim {@code hasRole("ELEITOR")} funciona.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("papeis");
        autoridades.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(autoridades);
        return converter;
    }
}
