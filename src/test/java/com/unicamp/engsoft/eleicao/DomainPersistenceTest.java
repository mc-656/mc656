package com.unicamp.engsoft.eleicao;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoMaioria;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DomainPersistenceTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;

    @Autowired VotacaoRepository votacaoRepository;

    @Autowired EntityManager entityManager;

    @Test
    void salvaERecuperaUsuario() {
        Usuario usuario =
                usuarioRepository.save(
                        new Usuario("Caio", "12345678909", "caio@example.com", "hash-da-senha"));

        Usuario recuperado = usuarioRepository.findById(usuario.getId()).orElseThrow();

        assertThat(recuperado.getNome()).isEqualTo("Caio");
        assertThat(recuperado.getCpf()).isEqualTo("12345678909");
        assertThat(recuperado.getEmail()).isEqualTo("caio@example.com");
    }

    @Test
    void salvaERecuperaVotacaoComEstadoInicial() {
        Instant inicio = Instant.parse("2026-10-01T12:00:00Z");
        Instant fim = Instant.parse("2026-10-02T12:00:00Z");

        Votacao votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA, inicio, fim, "Eleição", "Descrição"));

        Votacao recuperada = votacaoRepository.findById(votacao.getId()).orElseThrow();

        assertThat(recuperada.getTipo()).isEqualTo(TipoVotacao.ELEICAO_PRIVADA);
        assertThat(recuperada.getInicioEm()).isEqualTo(inicio);
        assertThat(recuperada.getFimEm()).isEqualTo(fim);
        assertThat(recuperada.getNome()).isEqualTo("Eleição");
        assertThat(recuperada.getDescricao()).isEqualTo("Descrição");
        assertThat(recuperada.getEstado()).isEqualTo(EstadoVotacao.RascunhoVotacao);
    }

    @Test
    void salvaERecuperaVotacaoComRegras() {
        // Arrange
        Votacao votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2099-10-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Eleição",
                                "Descrição"));
        votacao.configurarRegras(
                new RegraVotacao(
                        TipoMaioria.MAIORIA_QUALIFICADA,
                        new BigDecimal("66.67"),
                        true,
                        new BigDecimal("40"),
                        true));

        // Act: força a escrita e releê do banco, fora da sessão
        entityManager.flush();
        entityManager.clear();
        RegraVotacao regras = votacaoRepository.findById(votacao.getId()).orElseThrow().getRegras();

        // Assert
        assertThat(regras).isNotNull();
        assertThat(regras.tipoMaioria()).isEqualTo(TipoMaioria.MAIORIA_QUALIFICADA);
        assertThat(regras.percentualQualificado()).isEqualByComparingTo("66.67");
        assertThat(regras.segundoTurno()).isTrue();
        assertThat(regras.quorumMinimoPercentual()).isEqualByComparingTo("40");
        assertThat(regras.votoPonderado()).isTrue();
    }

    @Test
    void votacaoSemRegrasRecuperaEmbeddedNulo() {
        // Arrange
        Votacao votacao =
                votacaoRepository.save(
                        new Votacao(
                                TipoVotacao.ELEICAO_PRIVADA,
                                Instant.parse("2099-10-01T12:00:00Z"),
                                Instant.parse("2099-10-02T12:00:00Z"),
                                "Eleição",
                                "Descrição"));

        // Act
        entityManager.flush();
        entityManager.clear();
        Votacao recuperada = votacaoRepository.findById(votacao.getId()).orElseThrow();

        // Assert: as cinco colunas ficam nulas e o VO vem nulo, sem estourar a validação do record
        assertThat(recuperada.getRegras()).isNull();
    }
}
