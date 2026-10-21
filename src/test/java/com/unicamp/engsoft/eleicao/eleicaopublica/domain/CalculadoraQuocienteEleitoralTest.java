package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculadoraQuocienteEleitoralTest {

    private static CandidatoProporcional c(String nome, long votos) {
        return new CandidatoProporcional(nome, votos);
    }

    private static PartidoProporcional p(
            String sigla, long legenda, CandidatoProporcional... candidatos) {
        return new PartidoProporcional(sigla, legenda, List.of(candidatos));
    }

    private static ResultadoPartido resultado(DistribuicaoCadeiras d, String sigla) {
        return d.partidos().stream()
                .filter(r -> r.partido().sigla().equals(sigla))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> nomesEleitos(DistribuicaoCadeiras d) {
        return d.eleitos().stream().map(CandidatoEleito::nome).toList();
    }

    @Test
    @DisplayName("Quociente eleitoral exato é a divisão simples")
    void quocienteExato() {
        // Arrange / Act
        long qe = CalculadoraQuocienteEleitoral.quocienteEleitoral(100_000, 5);

        // Assert
        assertThat(qe).isEqualTo(20_000);
    }

    @Test
    @DisplayName("Fração maior que meio arredonda o quociente eleitoral para cima (art. 106)")
    void fracaoMaiorQueMeioArredondaParaCima() {
        // Arrange / Act: 1001 / 3 = 333,67
        long qe = CalculadoraQuocienteEleitoral.quocienteEleitoral(1_001, 3);

        // Assert
        assertThat(qe).isEqualTo(334);
    }

    @Test
    @DisplayName("Fração igual ou menor que meio é desprezada no quociente eleitoral (art. 106)")
    void fracaoAteMeioEhDesprezada() {
        // Arrange / Act: 1003 / 2 = 501,5 e 1000 / 3 = 333,33
        long exatamenteMeio = CalculadoraQuocienteEleitoral.quocienteEleitoral(1_003, 2);
        long menorQueMeio = CalculadoraQuocienteEleitoral.quocienteEleitoral(1_000, 3);

        // Assert
        assertThat(exatamenteMeio).isEqualTo(501);
        assertThat(menorQueMeio).isEqualTo(333);
    }

    @Test
    @DisplayName("Quociente partidário despreza a fração (art. 107)")
    void quocientePartidarioDesprezaFracao() {
        // Arrange / Act: 45.000 / 20.000 = 2,25
        long qp = CalculadoraQuocienteEleitoral.quocientePartidario(45_000, 20_000);

        // Assert
        assertThat(qp).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Exemplo didático: candidato com 5 mil votos é eleito e outro com 13 mil fica de fora")
    void exemploDidatico() {
        // Arrange: 100 mil votos válidos, 5 vagas → QE = 20.000
        List<PartidoProporcional> partidos =
                List.of(
                        p(
                                "A",
                                1_000,
                                c("Ana", 26_000),
                                c("Bruno", 9_000),
                                c("Carla", 5_000),
                                c("Davi", 4_000)),
                        p("B", 1_000, c("Diego", 14_000), c("Elisa", 8_000), c("Fábio", 2_000)),
                        p("C", 500, c("Gabriela", 13_000), c("Hugo", 1_500)),
                        p("D", 500, c("Igor", 12_000), c("Júlia", 2_500)));

        // Act
        DistribuicaoCadeiras d = CalculadoraQuocienteEleitoral.distribuir(5, partidos);

        // Assert: quocientes
        assertThat(d.votosValidos()).isEqualTo(100_000);
        assertThat(d.quocienteEleitoral()).isEqualTo(20_000);
        assertThat(resultado(d, "A").quocientePartidario()).isEqualTo(2);
        assertThat(resultado(d, "B").quocientePartidario()).isEqualTo(1);
        assertThat(resultado(d, "C").quocientePartidario()).isZero();
        assertThat(resultado(d, "D").quocientePartidario()).isZero();

        // Assert: C e D (15 mil) não atingem 80% do QE (16 mil) e não disputam sobras
        assertThat(resultado(d, "C").atingiuClausulaDesempenho()).isFalse();
        assertThat(resultado(d, "D").atingiuClausulaDesempenho()).isFalse();

        // Assert: sobra 1 → A 45.000/3 = 15.000 vence B 25.000/2 = 12.500
        RodadaSobra primeira = d.rodadasSobras().get(0);
        assertThat(primeira.clausulaDesempenho()).isTrue();
        assertThat(primeira.medias())
                .extracting(MediaPartido::partido, MediaPartido::media)
                .containsExactly(
                        tuple("A", new BigDecimal("15000.00")),
                        tuple("B", new BigDecimal("12500.00")));
        assertThat(primeira.eleito().nome()).isEqualTo("Carla");

        // Assert: sobra 2 → A 45.000/4 = 11.250 perde para B 12.500
        RodadaSobra segunda = d.rodadasSobras().get(1);
        assertThat(segunda.medias())
                .extracting(MediaPartido::media)
                .containsExactly(new BigDecimal("11250.00"), new BigDecimal("12500.00"));
        assertThat(segunda.eleito().nome()).isEqualTo("Elisa");

        // Assert: resultado final
        assertThat(d.rodadasSobras()).hasSize(2);
        assertThat(nomesEleitos(d))
                .containsExactly("Ana", "Bruno", "Diego", "Carla", "Elisa")
                .doesNotContain("Gabriela", "Igor", "Davi");
        assertThat(resultado(d, "A").cadeirasTotais()).isEqualTo(3);
        assertThat(resultado(d, "B").cadeirasTotais()).isEqualTo(2);
        assertThat(d.vagasNaoPreenchidas()).isZero();
    }

    @Test
    @DisplayName("Vaga do quociente exige candidato com 10% do QE; sem ele, a vaga vira sobra")
    void candidatoAbaixoDeDezPorCentoNaoOcupaVagaDoQuociente() {
        // Arrange: V = 300, 3 vagas → QE = 100. X tem QP = 2, mas X2 tem só 5 votos (< 10)
        List<PartidoProporcional> partidos =
                List.of(p("X", 5, c("X1", 190), c("X2", 5)), p("Y", 0, c("Y1", 100)));

        // Act
        DistribuicaoCadeiras d = CalculadoraQuocienteEleitoral.distribuir(3, partidos);

        // Assert
        assertThat(resultado(d, "X").quocientePartidario()).isEqualTo(2);
        assertThat(resultado(d, "X").cadeirasPorQuociente()).isEqualTo(1);
        // Ninguém cumpre mais a cláusula 80/20: sobra vai às maiores médias entre todos (STF)
        assertThat(d.rodadasSobras())
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.clausulaDesempenho()).isFalse();
                            assertThat(r.eleito().nome()).isEqualTo("X2");
                            assertThat(r.eleito().criterio())
                                    .isEqualTo(CriterioEleicao.SOBRA_SEM_CLAUSULA);
                        });
    }

    @Test
    @DisplayName("Empate de médias favorece o partido com mais votos e depois a sigla")
    void empateDeMedias() {
        // Arrange: V = 300, 3 vagas → QE = 100; A e B têm QP 1 e média 75 na sobra
        List<PartidoProporcional> partidos =
                List.of(p("B", 0, c("b1", 100), c("b2", 50)), p("A", 0, c("a1", 100), c("a2", 50)));

        // Act
        DistribuicaoCadeiras d = CalculadoraQuocienteEleitoral.distribuir(3, partidos);

        // Assert
        assertThat(d.rodadasSobras().get(0).eleito().nome()).isEqualTo("a2");
    }

    @Test
    @DisplayName("Se nenhum partido atinge o QE, elegem-se os mais votados (art. 111)")
    void nenhumPartidoAtingeQuociente() {
        // Arrange: V = 34, 3 vagas → QE = 11; maior partido tem 10
        List<PartidoProporcional> partidos =
                List.of(
                        p("X", 0, c("X1", 10)),
                        p("Y", 0, c("Y1", 9)),
                        p("Z", 0, c("Z1", 8)),
                        p("W", 0, c("W1", 7)));

        // Act
        DistribuicaoCadeiras d = CalculadoraQuocienteEleitoral.distribuir(3, partidos);

        // Assert
        assertThat(d.quocienteEleitoral()).isEqualTo(11);
        assertThat(d.nenhumPartidoAtingiuQuociente()).isTrue();
        assertThat(nomesEleitos(d)).containsExactly("X1", "Y1", "Z1");
        assertThat(d.eleitos()).allMatch(e -> e.criterio() == CriterioEleicao.MAIS_VOTADOS);
    }

    @Test
    @DisplayName("Vagas sem candidatos suficientes ficam vazias")
    void vagasSemCandidatos() {
        // Arrange: um partido com um só candidato e 3 vagas
        List<PartidoProporcional> partidos = List.of(p("X", 0, c("X1", 100)));

        // Act
        DistribuicaoCadeiras d = CalculadoraQuocienteEleitoral.distribuir(3, partidos);

        // Assert
        assertThat(nomesEleitos(d)).containsExactly("X1");
        assertThat(d.vagasNaoPreenchidas()).isEqualTo(2);
    }

    @Test
    @DisplayName("Entradas inválidas são rejeitadas com DadosInvalidosException")
    void entradasInvalidas() {
        // Arrange
        List<PartidoProporcional> umPartido = List.of(p("X", 0, c("X1", 100)));

        // Act / Assert
        assertThatThrownBy(() -> CalculadoraQuocienteEleitoral.distribuir(0, umPartido))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> CalculadoraQuocienteEleitoral.distribuir(3, List.of()))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(
                        () ->
                                CalculadoraQuocienteEleitoral.distribuir(
                                        3, List.of(p("X", 1), p("X", 1))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> CalculadoraQuocienteEleitoral.distribuir(3, List.of(p("X", 1))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("zero");
        assertThatThrownBy(
                        () ->
                                CalculadoraQuocienteEleitoral.distribuir(
                                        3, List.of(p("PX", 1), p(" px ", 1))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> p("X", 0, c("Ana", 1), c("ana", 2)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("mesmo nome");
        assertThatThrownBy(() -> c("X1", -1)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> p(" ", 0)).isInstanceOf(DadosInvalidosException.class);
    }
}
