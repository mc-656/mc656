package com.unicamp.engsoft.eleicao.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Ida e volta entre encoder e decoder, sem subir o Spring.
 *
 * <p>Monta o mesmo par de beans que {@code JwtConfig} produz, com chave fixa. O que este teste
 * protege é o acordo entre os dois lados: algoritmo, nome das claims e validade. Uma mudança em
 * qualquer um deles quebra o login em produção sem quebrar compilação.
 */
class TokenServiceTest {

    private static final String SEGREDO =
            "segredo-de-teste-apenas-com-tamanho-suficiente-para-hs256";
    private static final String EMISSOR = "eleicao-api";

    private static final SecretKeySpec CHAVE =
            new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(CHAVE));

    private final JwtDecoder decoder = criarDecoder();

    private static JwtDecoder criarDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(CHAVE).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISSOR));
        return decoder;
    }

    private static UsuarioAutenticado eleitor() {
        Usuario usuario =
                new Usuario(
                        "Caio",
                        "12345678909",
                        "caio@example.com",
                        "$2a$10$hash",
                        PapelUsuario.ELEITOR);
        // O id vem do banco em produção; aqui é fixado porque é ele que vai no sub.
        usuario.setId(UUID.randomUUID());
        return UsuarioAutenticado.de(usuario);
    }

    @Test
    @DisplayName("O token emitido é aceito pelo decoder e carrega as claims esperadas")
    void emiteTokenQueODecoderAceita() {
        TokenService servico = new TokenService(encoder, EMISSOR, Duration.ofMinutes(120));
        UsuarioAutenticado usuario = eleitor();

        Jwt decodificado = decoder.decode(servico.gerar(usuario));

        assertThat(decodificado.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(decodificado.getClaimAsString("iss")).isEqualTo(EMISSOR);
        assertThat(decodificado.getClaimAsString("email")).isEqualTo("caio@example.com");
        assertThat(decodificado.getClaimAsStringList("papeis")).containsExactly("ROLE_ELEITOR");
    }

    @Test
    @DisplayName("O sub é o id, não o e-mail: o e-mail pode mudar, o identificador não")
    void usaOIdComoSubject() {
        TokenService servico = new TokenService(encoder, EMISSOR, Duration.ofMinutes(120));
        UsuarioAutenticado usuario = eleitor();

        Jwt decodificado = decoder.decode(servico.gerar(usuario));

        assertThat(decodificado.getSubject()).isNotEqualTo(usuario.getUsername());
    }

    @Test
    @DisplayName("A expiração respeita a validade configurada")
    void respeitaAValidadeConfigurada() {
        TokenService servico = new TokenService(encoder, EMISSOR, Duration.ofMinutes(30));
        Instant antes = Instant.now();

        Jwt decodificado = decoder.decode(servico.gerar(eleitor()));

        assertThat(decodificado.getExpiresAt())
                .isBetween(
                        antes.plus(Duration.ofMinutes(29)),
                        Instant.now().plus(Duration.ofMinutes(31)));
    }

    @Test
    @DisplayName("Token vencido é recusado na decodificação")
    void recusaTokenVencido() {
        // O encoder se recusa a emitir com exp no passado, então o token vencido é montado aqui.
        // Dez minutos atrás: bem além da tolerância de 60s do validador de timestamp.
        Instant passado = Instant.now().minus(Duration.ofMinutes(10));
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(EMISSOR)
                        .issuedAt(passado)
                        .expiresAt(passado.plus(Duration.ofMinutes(5)))
                        .subject(UUID.randomUUID().toString())
                        .build();
        String vencido =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();

        assertThatThrownBy(() -> decoder.decode(vencido))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Token de outro emissor é recusado")
    void recusaTokenDeOutroEmissor() {
        TokenService intruso = new TokenService(encoder, "outra-api", Duration.ofMinutes(120));
        String forasteiro = intruso.gerar(eleitor());

        assertThatThrownBy(() -> decoder.decode(forasteiro))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Token assinado com outra chave é recusado")
    void recusaAssinaturaDeOutraChave() {
        SecretKeySpec outraChave =
                new SecretKeySpec(
                        "outro-segredo-completamente-diferente-mas-longo"
                                .getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");
        TokenService falsificador =
                new TokenService(
                        new NimbusJwtEncoder(new ImmutableSecret<>(outraChave)),
                        EMISSOR,
                        Duration.ofMinutes(120));
        String falsificado = falsificador.gerar(eleitor());

        assertThatThrownBy(() -> decoder.decode(falsificado)).isInstanceOf(Exception.class);
    }
}
