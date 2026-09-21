package com.unicamp.engsoft.eleicao;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DomainPersistenceTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Test
    void salvaERecuperaUsuario() {
        Usuario usuario =
                usuarioRepository.save(
                        new Usuario(
                                "caiomaia",
                                "Caio",
                                "caio@example.com",
                                "hash-da-senha",
                                Set.of(PapelUsuario.ELEITOR)));

        Usuario recuperado = usuarioRepository.findById(usuario.getId()).orElseThrow();

        assertThat(recuperado.getUsername()).isEqualTo("caio");
        assertThat(recuperado.getEmail()).isEqualTo("caio@example.com");
        assertThat(recuperado.getPapeis()).containsExactly(PapelUsuario.ELEITOR);
    }

    @Test
    void salvaERecuperaVotacaoComEstadoInicial() {
        Instant inicio = Instant.parse("2026-10-01T12:00:00Z");
        Instant fim = Instant.parse("2026-10-02T12:00:00Z");

        Votacao votacao =
                votacaoRepository.save(new Votacao(TipoVotacao.ELEICAO_PRIVADA, inicio, fim));

        Votacao recuperada = votacaoRepository.findById(votacao.getId()).orElseThrow();

        assertThat(recuperada.getTipo()).isEqualTo(TipoVotacao.ELEICAO_PRIVADA);
        assertThat(recuperada.getInicioEm()).isEqualTo(inicio);
        assertThat(recuperada.getFimEm()).isEqualTo(fim);
        assertThat(recuperada.getEstado()).isEqualTo(EstadoVotacao.RascunhoVotacao);
    }
}
