package com.unicamp.engsoft.eleicao.votacao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Papéis por votação (RF-03) contra o schema real da V3. */
@Transactional
class PapelVotacaoRepositoryTest extends AbstractIntegrationTest {

    @Autowired PapelVotacaoRepository papelVotacaoRepository;

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    private Usuario usuario;
    private Votacao votacao;
    private Votacao outraVotacao;

    @BeforeEach
    void semeia() {
        usuario =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));
        votacao = votacaoRepository.save(novaVotacao());
        outraVotacao = votacaoRepository.save(novaVotacao());
    }

    private static Votacao novaVotacao() {
        return new Votacao(
                TipoVotacao.ELEICAO_PRIVADA,
                Instant.parse("2026-10-01T12:00:00Z"),
                Instant.parse("2026-10-02T12:00:00Z"));
    }

    private void atribui(Votacao alvo, PapelUsuario papel) {
        papelVotacaoRepository.saveAndFlush(new PapelVotacao(usuario.getId(), alvo.getId(), papel));
    }

    private boolean tem(Votacao alvo, PapelUsuario papel) {
        return papelVotacaoRepository.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                usuario.getId(), alvo.getId(), papel);
    }

    @Test
    @DisplayName("Papel vale só na votação em que foi atribuído")
    void papelValeSoNaVotacaoAtribuida() {
        atribui(votacao, PapelUsuario.ADMIN_VOTACAO);

        assertThat(tem(votacao, PapelUsuario.ADMIN_VOTACAO)).isTrue();
        assertThat(tem(votacao, PapelUsuario.ELEITOR)).isFalse();
        assertThat(tem(outraVotacao, PapelUsuario.ADMIN_VOTACAO)).isFalse();
    }

    @Test
    @DisplayName("O mesmo usuário pode ser admin e eleitor na mesma votação")
    void acumulaOsDoisPapeisNaMesmaVotacao() {
        atribui(votacao, PapelUsuario.ADMIN_VOTACAO);
        atribui(votacao, PapelUsuario.ELEITOR);

        assertThat(papelVotacaoRepository.findByIdVotacaoId(votacao.getId()))
                .extracting(p -> p.getId().getPapel())
                .containsExactlyInAnyOrder(PapelUsuario.ADMIN_VOTACAO, PapelUsuario.ELEITOR);
    }

    @Test
    @DisplayName("Lista os papéis do usuário em todas as votações")
    void listaPapeisDoUsuario() {
        atribui(votacao, PapelUsuario.ADMIN_VOTACAO);
        atribui(outraVotacao, PapelUsuario.ELEITOR);

        assertThat(papelVotacaoRepository.findByIdUsuarioId(usuario.getId()))
                .extracting(p -> p.getId().getVotacaoId())
                .containsExactlyInAnyOrder(votacao.getId(), outraVotacao.getId());
    }

    @Test
    @DisplayName("Apagar a votação apaga os papéis dela (ON DELETE CASCADE)")
    void apagarVotacaoApagaPapeis() {
        atribui(votacao, PapelUsuario.ELEITOR);

        votacaoRepository.delete(votacao);
        votacaoRepository.flush();

        assertThat(papelVotacaoRepository.findByIdVotacaoId(votacao.getId())).isEmpty();
    }

    @Test
    @DisplayName("Apagar o usuário apaga os papéis dele (ON DELETE CASCADE)")
    void apagarUsuarioApagaPapeis() {
        atribui(votacao, PapelUsuario.ELEITOR);

        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        assertThat(papelVotacaoRepository.findByIdUsuarioId(usuario.getId())).isEmpty();
    }
}
