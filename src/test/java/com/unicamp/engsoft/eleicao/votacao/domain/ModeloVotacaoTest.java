package com.unicamp.engsoft.eleicao.votacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ModeloVotacaoTest {

    @Test
    @DisplayName("Os três modelos exigidos pela RF-16 usam maioria simples")
    void modelosUsamMaioriaSimples() {
        // Act + Assert
        assertThat(ModeloVotacao.values())
                .extracting(m -> m.regra().tipoMaioria())
                .containsOnly(TipoMaioria.MAIORIA_SIMPLES);
    }

    @Test
    @DisplayName("Assembleia exige quórum de 50%; os demais não têm quórum")
    void quorumDaAssembleia() {
        // Act + Assert
        assertThat(ModeloVotacao.ASSEMBLEIA.regra().quorumMinimoPercentual())
                .isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(ModeloVotacao.ELEICAO_CHAPA.regra().quorumMinimoPercentual()).isNull();
        assertThat(ModeloVotacao.DECISAO_SIM_NAO.regra().quorumMinimoPercentual()).isNull();
    }

    @Test
    @DisplayName("Decisão sim/não traz Sim e Não como opções padrão")
    void opcoesPadraoDaDecisaoSimNao() {
        // Act + Assert
        assertThat(ModeloVotacao.DECISAO_SIM_NAO.opcoesPadrao()).containsExactly("Sim", "Não");
        assertThat(ModeloVotacao.ELEICAO_CHAPA.opcoesPadrao()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ModeloVotacao.class)
    @DisplayName("A descrição do modelo cabe na coluna de descrição da votação")
    void descricaoCabeNaVotacao(ModeloVotacao modelo) {
        // Act + Assert: a descrição do modelo vira a descrição padrão da votação (varchar 100)
        assertThat(modelo.descricao()).hasSizeLessThanOrEqualTo(100);
        assertThat(modelo.votoSecreto()).isTrue();
    }
}
