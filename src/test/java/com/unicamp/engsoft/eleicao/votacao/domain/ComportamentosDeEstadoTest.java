package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Um estado responde por si: quem permite, quem nega e com que mensagem (padrão State, SPECS §4.2).
 */
class ComportamentosDeEstadoTest {

    /** Operações que só o rascunho permite (RF-10, RF-11, RF-12). */
    private static final Set<OperacaoVotacao> CONFIGURACAO =
            EnumSet.of(
                    OperacaoVotacao.CONFIGURAR_REGRAS,
                    OperacaoVotacao.GERENCIAR_CANDIDATOS,
                    OperacaoVotacao.GERENCIAR_ELEITORES);

    /** Operações do convite (RF-17), permitidas só em Publicada e EmVotacao. */
    private static final Set<OperacaoVotacao> CONVITE =
            EnumSet.of(OperacaoVotacao.CONVIDAR_ELEITORES, OperacaoVotacao.ENTRAR_POR_CONVITE);

    @ParameterizedTest
    @EnumSource(EstadoVotacao.class)
    @DisplayName("Todo estado tem comportamento registrado e ele é do próprio estado")
    void todoEstadoTemComportamento(EstadoVotacao estado) {
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        assertThat(comportamento).isNotNull();
        assertThat(comportamento.estado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("RascunhoVotacao permite todas as operações de configuração")
    void rascunhoPermiteOperacoesDeConfiguracao() {
        EstadoVotacaoComportamento comportamento =
                ComportamentosDeEstado.de(EstadoVotacao.RascunhoVotacao);

        // Act / Assert
        for (OperacaoVotacao operacao : CONFIGURACAO) {
            assertThatCode(() -> comportamento.exigir(operacao)).doesNotThrowAnyException();
            assertThat(comportamento.permite(operacao)).isTrue();
        }
        // O convite só nasce depois da publicação (RF-17).
        for (OperacaoVotacao operacao : CONVITE) {
            assertThat(comportamento.permite(operacao)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = {"Publicada", "EmVotacao"})
    @DisplayName("Publicada e EmVotacao aceitam convite, mas não configuração")
    void publicadaEEmVotacaoAceitamConvite(EstadoVotacao estado) {
        // Arrange
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        // Act / Assert
        for (OperacaoVotacao operacao : CONVITE) {
            assertThatCode(() -> comportamento.exigir(operacao)).doesNotThrowAnyException();
        }
        for (OperacaoVotacao operacao : CONFIGURACAO) {
            assertThat(comportamento.permite(operacao)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = {"RascunhoVotacao", "Publicada", "EmVotacao"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Fora de Publicada e EmVotacao o link de convite deixa de incluir eleitores")
    void conviteRecusadoForaDaJanela(EstadoVotacao estado) {
        // Arrange
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        // Act / Assert
        assertThatThrownBy(() -> comportamento.exigir(OperacaoVotacao.ENTRAR_POR_CONVITE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Publicada, EmVotacao")
                .hasMessageContaining(estado.name());
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = "RascunhoVotacao",
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Nenhum outro estado (inclusive os finais) permite operação de configuração")
    void estadosForaDoRascunhoNaoPermitemOperacoes(EstadoVotacao estado) {
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        for (OperacaoVotacao operacao : CONFIGURACAO) {
            assertThat(comportamento.permite(operacao)).isFalse();
            assertThatThrownBy(() -> comportamento.exigir(operacao))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining(operacao.rotulo())
                    .hasMessageContaining(estado.name());
        }
    }

    @Test
    @DisplayName("Todas as operações respondem com o mesmo texto, mudando só o rótulo")
    void mensagensSaoConsistentesEntreOperacoes() {
        EstadoVotacaoComportamento publicada = ComportamentosDeEstado.de(EstadoVotacao.Publicada);

        String modelo =
                publicada
                        .mensagemDeErro(OperacaoVotacao.CONFIGURAR_REGRAS)
                        .replace(OperacaoVotacao.CONFIGURAR_REGRAS.rotulo(), "{operacao}");

        // Diz qual estado é o exigido e em qual a votação está — e faz isso igual para todas.
        assertThat(modelo)
                .contains("{operacao}")
                .contains("só pode ser feita com a votação em RascunhoVotacao")
                .contains("(estado atual: Publicada)");
        for (OperacaoVotacao operacao : CONFIGURACAO) {
            assertThat(publicada.mensagemDeErro(operacao))
                    .isEqualTo(
                            modelo.replace("{operacao}", operacao.rotulo()),
                            "a mensagem de '%s' divergiu do modelo comum"
                                    .formatted(operacao.rotulo()));
        }
    }
}
