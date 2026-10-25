package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Um estado responde por si: quem permite, quem nega e com que mensagem (padrão State, SPECS §4.2).
 */
class ComportamentosDeEstadoTest {

    @ParameterizedTest
    @EnumSource(EstadoVotacao.class)
    @DisplayName("Todo estado tem comportamento registrado e ele é do próprio estado")
    void todoEstadoTemComportamento(EstadoVotacao estado) {
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        assertThat(comportamento).isNotNull();
        assertThat(comportamento.estado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("RascunhoVotacao permite as três operações de configuração")
    void rascunhoPermiteOperacoesDeConfiguracao() {
        EstadoVotacaoComportamento comportamento =
                ComportamentosDeEstado.de(EstadoVotacao.RascunhoVotacao);

        for (OperacaoVotacao operacao : OperacaoVotacao.values()) {
            assertThatCode(() -> comportamento.exigir(operacao)).doesNotThrowAnyException();
            assertThat(comportamento.permite(operacao)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoVotacao.class,
            names = "RascunhoVotacao",
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Nenhum outro estado (inclusive os finais) permite operação de escrita")
    void estadosForaDoRascunhoNaoPermitemOperacoes(EstadoVotacao estado) {
        EstadoVotacaoComportamento comportamento = ComportamentosDeEstado.de(estado);

        for (OperacaoVotacao operacao : OperacaoVotacao.values()) {
            assertThat(comportamento.permite(operacao)).isFalse();
            assertThatThrownBy(() -> comportamento.exigir(operacao))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining(operacao.rotulo())
                    .hasMessageContaining(estado.name());
        }
    }

    @Test
    @DisplayName("As três operações respondem com o mesmo texto, mudando só o rótulo")
    void mensagensSaoConsistentesEntreOperacoes() {
        EstadoVotacaoComportamento publicada = ComportamentosDeEstado.de(EstadoVotacao.Publicada);

        String modelo =
                publicada
                        .mensagemDeErro(OperacaoVotacao.CONFIGURAR_REGRAS)
                        .replace(OperacaoVotacao.CONFIGURAR_REGRAS.rotulo(), "{operacao}");

        // Diz qual estado é o exigido e em qual a votação está — e faz isso igual para as três.
        assertThat(modelo)
                .contains("{operacao}")
                .contains("só pode ser feita com a votação em RascunhoVotacao")
                .contains("(estado atual: Publicada)");
        for (OperacaoVotacao operacao : OperacaoVotacao.values()) {
            assertThat(publicada.mensagemDeErro(operacao))
                    .isEqualTo(
                            modelo.replace("{operacao}", operacao.rotulo()),
                            "a mensagem de '%s' divergiu do modelo comum"
                                    .formatted(operacao.rotulo()));
        }
    }
}
