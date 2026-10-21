package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.CasoContraIntuitivo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.FonteLegal;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.SecaoDidatica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExplicacaoQuocienteEleitoralServiceTest {

    /**
     * Uma linha de leitura tem por volta de 80 caracteres; 5 linhas ≈ 400 (critério da issue #90).
     */
    private static final int LIMITE_PARAGRAFO = 400;

    private final ExplicacaoQuocienteEleitoralResponse explicacao =
            new ExplicacaoQuocienteEleitoralService().explicar();

    @Test
    @DisplayName("Nenhum parágrafo passa de 5 linhas de leitura (RNF-07)")
    void paragrafosCurtos() {
        // Arrange / Act
        var paragrafos = explicacao.secoes().stream().flatMap(s -> s.paragrafos().stream());

        // Assert
        assertThat(explicacao.secoes()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(paragrafos)
                .allSatisfy(p -> assertThat(p).hasSizeLessThanOrEqualTo(LIMITE_PARAGRAFO));
        assertThat(explicacao.exemplo().passos())
                .allSatisfy(p -> assertThat(p).hasSizeLessThanOrEqualTo(LIMITE_PARAGRAFO));
    }

    @Test
    @DisplayName("Texto usa os números calculados pela calculadora")
    void textoReflecteCalculo() {
        // Arrange / Act
        SecaoDidatica passo1 =
                explicacao.secoes().stream()
                        .filter(s -> s.titulo().contains("quociente eleitoral"))
                        .findFirst()
                        .orElseThrow();

        // Assert
        assertThat(passo1.paragrafos())
                .anyMatch(p -> p.contains("100.000") && p.contains("20.000"));
        assertThat(explicacao.exemplo().calculoQuocienteEleitoral())
                .isEqualTo("100.000 ÷ 5 = 20.000");
    }

    @Test
    @DisplayName("Caso contraintuitivo compara o eleito menos votado com o não eleito mais votado")
    void casoContraIntuitivo() {
        // Arrange / Act
        CasoContraIntuitivo caso = explicacao.exemplo().casoContraIntuitivo();

        // Assert
        assertThat(caso.eleitoComMenosVotos().nome()).isEqualTo("Carla");
        assertThat(caso.eleitoComMenosVotos().votos()).isEqualTo(5_000);
        assertThat(caso.naoEleitoComMaisVotos().nome()).isEqualTo("Gabriela");
        assertThat(caso.naoEleitoComMaisVotos().votos()).isEqualTo(13_000);
        assertThat(caso.explicacao()).contains("5.000", "13.000", "15.000");
    }

    @Test
    @DisplayName("Base legal aponta para as fontes oficiais no planalto.gov.br")
    void baseLegal() {
        // Arrange / Act / Assert
        assertThat(explicacao.baseLegal())
                .extracting(FonteLegal::url)
                .allMatch(u -> u.startsWith("https://www.planalto.gov.br/"));
        assertThat(explicacao.baseLegal())
                .extracting(FonteLegal::norma)
                .anyMatch(n -> n.contains("4.737"))
                .anyMatch(n -> n.contains("14.211"));
    }
}
