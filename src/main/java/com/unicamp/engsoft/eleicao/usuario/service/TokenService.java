package com.unicamp.engsoft.eleicao.usuario.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String emitter;
    private final Duration expiresIn;

    public TokenService(
            JwtEncoder encoder,
            @Value("${jwt.emitter}") String emitter,
            @Value("${jwt.expiresIn}") Duration expiresIn) {
        this.encoder = encoder;
        this.emitter = emitter;
        this.expiresIn = expiresIn;
    }

    public String gerar(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();

        List<String> papeis = usuario.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emitter)
                .issuedAt(agora)
                .expiresAt(agora.plus(expiresIn))
                .subject(usuario.getId().toString())
                .claim("email", usuario.getUsername())
                .claim("papeis", papeis)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
