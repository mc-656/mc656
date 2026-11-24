package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.votacao.domain.AlocacaoApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.PropostaApuravel;
import com.unicamp.engsoft.eleicao.votacao.domain.ResultadoApuracaoOrcamento;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusApuracao;
import com.unicamp.engsoft.eleicao.votacao.domain.StatusPropostaApurada;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoDesempate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MotorApuracaoOrcamentoTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private final MotorApuracaoOrcamento motor = new MotorApuracaoOrcamento();

    private static PropostaApuravel proposta(
            String titulo, String custo, Instant criadoEm, UUID id) {
        return new PropostaApuravel(id, titulo, new BigDecimal(custo), criadoEm);
    }

    private static PropostaApuravel proposta(String titulo, String custo, Instant criadoEm) {
        return proposta(titulo, custo, criadoEm, UUID.randomUUID());
    }

    @Nested
    @DisplayName("Seleção por cotas")
    class SelecaoPorCotas {

        @Test
        @DisplayName(
                "Seleciona por pontos decrescentes, pula proposta cara e aproveita saldo exato")
        void selecionaEPulaCaraComSaldoExato() {
            PropostaApuravel p1 = proposta("Parque", "60.00", T0);
            PropostaApuravel p2 = proposta("Hospital", "50.00", T0.plusSeconds(10));
            PropostaApuravel p3 = proposta("Ciclovia", "40.00", T0.plusSeconds(20));
            PropostaApuravel p4 = proposta("Praca", "10.00", T0.plusSeconds(30));

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("100.00"),
                            null,
                            List.of(p1, p2, p3, p4),
                            List.of(
                                    new AlocacaoApuravel(p1.id(), new BigDecimal("50")),
                                    new AlocacaoApuravel(p2.id(), new BigDecimal("30")),
                                    new AlocacaoApuravel(p3.id(), new BigDecimal("20")),
                                    new AlocacaoApuravel(p3.id(), new BigDecimal("0")),
                                    new AlocacaoApuravel(p4.id(), new BigDecimal("5"))),
                            10,
                            5,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.quorumAtingido()).isTrue();
            assertThat(resultado.totalEleitoresAptos()).isEqualTo(10);
            assertThat(resultado.totalParticipantes()).isEqualTo(5);
            assertThat(resultado.percentualComparecimento()).isEqualByComparingTo("50.00");
            assertThat(resultado.orcamentoTotal()).isEqualByComparingTo("100.00");
            assertThat(resultado.orcamentoUtilizado()).isEqualByComparingTo("100.00");
            assertThat(resultado.saldoRestante()).isEqualByComparingTo("0.00");
            assertThat(resultado.totalPontosComputados()).isEqualByComparingTo("105");
            assertThat(resultado.propostas())
                    .containsExactly(
                            new PropostaApurada(
                                    p1.id(),
                                    new BigDecimal("50"),
                                    new BigDecimal("60.00"),
                                    StatusPropostaApurada.SELECIONADA),
                            new PropostaApurada(
                                    p2.id(),
                                    new BigDecimal("30"),
                                    new BigDecimal("50.00"),
                                    StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO),
                            new PropostaApurada(
                                    p3.id(),
                                    new BigDecimal("20"),
                                    new BigDecimal("40.00"),
                                    StatusPropostaApurada.SELECIONADA),
                            new PropostaApurada(
                                    p4.id(),
                                    new BigDecimal("5"),
                                    new BigDecimal("10.00"),
                                    StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO));
        }
    }

    @Nested
    @DisplayName("Desempate e quórum")
    class DesempateEQuorum {

        @Test
        @DisplayName("DesempateMenorCusto prioriza proposta de menor custo (#95)")
        void menorCustoVenceEmpate() {
            PropostaApuravel cara = proposta("Ginásio", "70.00", T0);
            PropostaApuravel barata = proposta("Creche", "30.00", T0.plusSeconds(10));
            PropostaApuravel semVoto = proposta("Horta", "20.00", T0.plusSeconds(20));

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("70.00"),
                            new BigDecimal("50.00"),
                            List.of(cara, barata, semVoto),
                            List.of(
                                    new AlocacaoApuravel(cara.id(), new BigDecimal("20")),
                                    new AlocacaoApuravel(barata.id(), new BigDecimal("20.00"))),
                            4,
                            2,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.orcamentoUtilizado()).isEqualByComparingTo("50.00");
            assertThat(resultado.saldoRestante()).isEqualByComparingTo("20.00");
            assertThat(resultado.propostas())
                    .extracting(PropostaApurada::propostaId, PropostaApurada::status)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    barata.id(), StatusPropostaApurada.SELECIONADA),
                            org.assertj.core.groups.Tuple.tuple(
                                    cara.id(), StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO),
                            org.assertj.core.groups.Tuple.tuple(
                                    semVoto.id(), StatusPropostaApurada.SELECIONADA));
        }

        @Test
        @DisplayName("Quando empatadas de mesmo custo cabem juntas, todas são selecionadas")
        void empatadasMesmoCustoCabemJuntas() {
            PropostaApuravel a = proposta("Zebra", "25.00", T0);
            PropostaApuravel b = proposta("Alfa", "25.00", T0.plusSeconds(1));

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("50.00"),
                            null,
                            List.of(a, b),
                            List.of(
                                    new AlocacaoApuravel(a.id(), new BigDecimal("10")),
                                    new AlocacaoApuravel(b.id(), new BigDecimal("10"))),
                            2,
                            2,
                            new DesempateMenorCusto(null));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(resultado.orcamentoUtilizado()).isEqualByComparingTo("50.00");
            assertThat(resultado.saldoRestante()).isEqualByComparingTo("0.00");
            assertThat(resultado.propostas())
                    .allSatisfy(
                            p ->
                                    assertThat(p.status())
                                            .isEqualTo(StatusPropostaApurada.SELECIONADA));
        }

        @Test
        @DisplayName("Quando empatadas excedem saldo e apenas subconjunto cabe, marca empate")
        void empatadasMesmoCustoNaoCabemTodasMarcaEmpate() {
            PropostaApuravel a = proposta("Alfa", "30.00", T0);
            PropostaApuravel b = proposta("Beta", "30.00", T0.plusSeconds(5));
            PropostaApuravel nenhuma = proposta("Gama", "45.00", T0.plusSeconds(10));
            PropostaApuravel nenhuma2 = proposta("Delta", "45.00", T0.plusSeconds(15));

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("40.00"),
                            null,
                            List.of(a, b, nenhuma, nenhuma2),
                            List.of(
                                    new AlocacaoApuravel(a.id(), new BigDecimal("20")),
                                    new AlocacaoApuravel(b.id(), new BigDecimal("20")),
                                    new AlocacaoApuravel(nenhuma.id(), new BigDecimal("10")),
                                    new AlocacaoApuravel(nenhuma2.id(), new BigDecimal("10"))),
                            4,
                            4,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.orcamentoUtilizado()).isEqualByComparingTo("0.00");
            assertThat(resultado.saldoRestante()).isEqualByComparingTo("40.00");
            assertThat(resultado.propostas())
                    .extracting(PropostaApurada::status)
                    .containsExactly(
                            StatusPropostaApurada.EMPATADA_SEM_DECISAO,
                            StatusPropostaApurada.EMPATADA_SEM_DECISAO,
                            StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO,
                            StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO);
        }

        @Test
        @DisplayName("Desempate secundário alfabético resolve igualdade de custo por título e id")
        void desempateSecundarioAlfabetico() {
            UUID idMenor = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID idMaior = UUID.fromString("00000000-0000-0000-0000-000000000002");
            PropostaApuravel segunda = proposta("Biblioteca", "40.00", T0, idMaior);
            PropostaApuravel primeira = proposta("agua", "40.00", T0.plusSeconds(5), idMenor);

            ResultadoApuracaoOrcamento r1 =
                    motor.apurar(
                            new BigDecimal("40.00"),
                            null,
                            List.of(segunda, primeira),
                            List.of(
                                    new AlocacaoApuravel(segunda.id(), new BigDecimal("10")),
                                    new AlocacaoApuravel(primeira.id(), new BigDecimal("10"))),
                            2,
                            2,
                            new DesempateMenorCusto(TipoDesempate.ORDEM_ALFABETICA));

            assertThat(r1.status()).isEqualTo(StatusApuracao.DEFINIDO);
            assertThat(r1.propostas().get(0).propostaId()).isEqualTo(primeira.id());
            assertThat(r1.propostas().get(0).status()).isEqualTo(StatusPropostaApurada.SELECIONADA);
            assertThat(r1.propostas().get(1).status())
                    .isEqualTo(StatusPropostaApurada.NAO_SELECIONADA_ORCAMENTO);

            // Mesmo título (case-insensitive): desempate determinístico por id
            PropostaApuravel mesmaLetraMaiorId = proposta("Mesmo", "40.00", T0, idMaior);
            PropostaApuravel mesmaLetraMenorId =
                    proposta("mesmo", "40.00", T0.plusSeconds(1), idMenor);
            List<List<PropostaApuravel>> ordenadas =
                    new DesempateMenorCusto(TipoDesempate.ORDEM_ALFABETICA)
                            .ordenarEmpatadas(List.of(mesmaLetraMaiorId, mesmaLetraMenorId));
            assertThat(ordenadas)
                    .extracting(sub -> sub.getFirst().id())
                    .containsExactly(idMenor, idMaior);
        }

        @Test
        @DisplayName("Empate parcial em estratégia customizada testa soma > saldo e allMatch")
        void estrategiaCustomizadaEmpateParcial() {
            PropostaApuravel cara = proposta("Cara", "60.00", T0);
            PropostaApuravel cabe = proposta("Cabe", "30.00", T0.plusSeconds(1));

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("50.00"),
                            null,
                            List.of(cara, cabe),
                            List.of(
                                    new AlocacaoApuravel(cara.id(), new BigDecimal("10")),
                                    new AlocacaoApuravel(cabe.id(), new BigDecimal("10"))),
                            2,
                            2,
                            empatadas -> List.of(empatadas));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.EMPATE);
            assertThat(resultado.propostas())
                    .allSatisfy(
                            p ->
                                    assertThat(p.status())
                                            .isEqualTo(StatusPropostaApurada.EMPATADA_SEM_DECISAO));
        }

        @Test
        @DisplayName("Quórum não atingido ou zero eleitores impede seleção de propostas")
        void quorumNaoAtingido() {
            PropostaApuravel p1 = proposta("P1", "10.00", T0.plusSeconds(10));
            PropostaApuravel p2 = proposta("P2", "10.00", T0);

            ResultadoApuracaoOrcamento resultado =
                    motor.apurar(
                            new BigDecimal("50.00"),
                            new BigDecimal("50.00"),
                            List.of(p1, p2),
                            List.of(new AlocacaoApuravel(p1.id(), new BigDecimal("10"))),
                            4,
                            1,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));

            assertThat(resultado.status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
            assertThat(resultado.quorumAtingido()).isFalse();
            assertThat(resultado.percentualComparecimento()).isEqualByComparingTo("25.00");
            assertThat(resultado.orcamentoUtilizado()).isEqualByComparingTo("0");
            assertThat(resultado.saldoRestante()).isEqualByComparingTo("50.00");
            assertThat(resultado.propostas())
                    .extracting(PropostaApurada::propostaId, PropostaApurada::status)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    p1.id(), StatusPropostaApurada.QUORUM_NAO_ATINGIDO),
                            org.assertj.core.groups.Tuple.tuple(
                                    p2.id(), StatusPropostaApurada.QUORUM_NAO_ATINGIDO));

            // Zero eleitores aptos com quórum 0 exigido não atinge quórum e comparecimento é 0.00
            PropostaApuravel p3 = proposta("P3", "10.00", T0.plusSeconds(1));
            ResultadoApuracaoOrcamento zeroEleitores =
                    motor.apurar(
                            new BigDecimal("50.00"),
                            BigDecimal.ZERO,
                            List.of(p2, p3),
                            List.of(),
                            0,
                            0,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));

            assertThat(zeroEleitores.quorumAtingido()).isFalse();
            assertThat(zeroEleitores.percentualComparecimento()).isEqualByComparingTo("0.00");
            assertThat(zeroEleitores.propostas())
                    .extracting(PropostaApurada::propostaId)
                    .containsExactly(p2.id(), p3.id());

            // Zero eleitores aptos sem quórum exigido atinge quórum (percentualComparecimento =
            // 0.00)
            ResultadoApuracaoOrcamento zeroSemQuorum =
                    motor.apurar(
                            new BigDecimal("50.00"),
                            null,
                            List.of(p2),
                            List.of(),
                            0,
                            0,
                            new DesempateMenorCusto(TipoDesempate.NENHUM));
            assertThat(zeroSemQuorum.quorumAtingido()).isTrue();
            assertThat(zeroSemQuorum.percentualComparecimento()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Validação de entradas")
    class Validacoes {

        @Test
        @DisplayName("Rejeita parâmetros inválidos e imutabilidade do resultado")
        void rejeitaParametrosInvalidos() {
            PropostaApuravel p = proposta("P", "10.00", T0);
            DesempateMenorCusto desempate = new DesempateMenorCusto(TipoDesempate.NENHUM);

            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.ZERO,
                                            null,
                                            List.of(p),
                                            List.of(),
                                            1,
                                            0,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            new BigDecimal("-1"),
                                            null,
                                            List.of(p),
                                            List.of(),
                                            1,
                                            0,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.TEN,
                                            null,
                                            List.of(),
                                            List.of(),
                                            1,
                                            0,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.TEN,
                                            null,
                                            List.of(p),
                                            List.of(),
                                            -1,
                                            0,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.TEN,
                                            null,
                                            List.of(p),
                                            List.of(),
                                            1,
                                            -1,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.TEN,
                                            null,
                                            List.of(p),
                                            List.of(),
                                            1,
                                            2,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    motor.apurar(
                                            BigDecimal.TEN,
                                            null,
                                            List.of(p),
                                            List.of(
                                                    new AlocacaoApuravel(
                                                            UUID.randomUUID(), BigDecimal.ONE)),
                                            1,
                                            1,
                                            desempate))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> proposta("P", "0.00", T0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AlocacaoApuravel(p.id(), new BigDecimal("-0.1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
