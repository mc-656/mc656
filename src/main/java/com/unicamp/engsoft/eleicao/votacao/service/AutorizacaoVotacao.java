package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Autorização por votação (RF-03), para uso em {@code @PreAuthorize}:
 *
 * <pre>{@code @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")}</pre>
 *
 * <p>Consulta o banco a cada chamada em vez de ler o token: o papel pode ser concedido ou revogado
 * enquanto o JWT ainda é válido. O usuário é identificado pelo {@code sub}, que é o id.
 *
 * <p>Nas views (sessão + form login, SPECS §7.3) não há JWT: o principal da sessão é o {@link
 * UsuarioAutenticado} carregado no login, que já traz o id. Aceitar os dois formatos aqui mantém
 * uma única regra de autorização para a API e para as telas (#67).
 */
@Service("autorizacaoVotacao")
public class AutorizacaoVotacao {

    private final PapelVotacaoRepository papelVotacaoRepository;

    public AutorizacaoVotacao(PapelVotacaoRepository papelVotacaoRepository) {
        this.papelVotacaoRepository = papelVotacaoRepository;
    }

    public boolean ehAdmin(UUID votacaoId, Authentication autenticacao) {
        return temPapel(votacaoId, autenticacao, PapelUsuario.ADMIN_VOTACAO);
    }

    public boolean ehEleitor(UUID votacaoId, Authentication autenticacao) {
        return temPapel(votacaoId, autenticacao, PapelUsuario.ELEITOR);
    }

    /**
     * Na dúvida nega: sem token JWT nem sessão de {@link UsuarioAutenticado}, ou com {@code sub}
     * que não é UUID, não há papel.
     */
    private boolean temPapel(UUID votacaoId, Authentication autenticacao, PapelUsuario papel) {
        if (votacaoId == null) {
            return false;
        }
        UUID usuarioId = usuarioDe(autenticacao);
        if (usuarioId == null) {
            return false;
        }
        return papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                usuarioId, votacaoId, papel);
    }

    private static UUID usuarioDe(Authentication autenticacao) {
        if (autenticacao instanceof JwtAuthenticationToken token) {
            String sub = token.getToken().getSubject();
            if (sub == null) {
                return null;
            }
            try {
                return UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (autenticacao != null
                && autenticacao.isAuthenticated()
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.getId();
        }
        return null;
    }
}
