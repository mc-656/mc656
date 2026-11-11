package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse.Cargo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse.Sistema;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.TiposVotoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.TiposVotoResponse.TipoVoto;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Conteúdo didático de tipos de voto (#91) e cargos (#92): fatos que não podem regredir. */
class ConteudoDidaticoServiceTest {

    private final ConteudoDidaticoService service = new ConteudoDidaticoService();

    @Test
    @DisplayName("Nulo e branco não são votos válidos; legenda é")
    void validadeDosTiposDeVoto() {
        // Arrange
        TiposVotoResponse conteudo = service.tiposVoto();

        // Act
        Map<String, Boolean> validade =
                conteudo.tipos().stream()
                        .collect(Collectors.toMap(TipoVoto::id, TipoVoto::contaComoValido));

        // Assert
        assertThat(validade)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("nulo", false, "branco", false, "legenda", true));
        assertThat(conteudo.tipos())
                .allSatisfy(
                        t -> {
                            assertThat(t.oQueE()).isNotBlank();
                            assertThat(t.comoRegistrarNaUrna()).isNotBlank();
                            assertThat(t.efeitoNoResultado()).isNotBlank();
                        });
    }

    @Test
    @DisplayName("Tipos de voto apontam para a explicação do quociente e para o TSE")
    void linksDosTiposDeVoto() {
        // Arrange / Act
        TiposVotoResponse conteudo = service.tiposVoto();

        // Assert
        assertThat(conteudo.urlExplicacaoQuociente())
                .isEqualTo("/eleicoes-publicas/quociente-eleitoral");
        assertThat(conteudo.legendaNoQuociente().paragrafos())
                .anyMatch(p -> p.contains("quociente partidário"));
        assertThat(conteudo.fontes())
                .extracting(TiposVotoResponse.Fonte::url)
                .contains(ConteudoDidaticoService.URL_TSE);
    }

    @Test
    @DisplayName("Os sete cargos têm de 1 a 3 atribuições, sistema correto e âncora estável")
    void cargos() {
        // Arrange
        CargosResponse conteudo = service.cargos();

        // Act
        Map<String, Cargo> porId =
                conteudo.cargos().stream()
                        .collect(Collectors.toMap(Cargo::id, Function.identity()));

        // Assert
        assertThat(porId)
                .containsOnlyKeys(
                        "presidente",
                        "governador",
                        "senador",
                        "deputado-federal",
                        "deputado-estadual",
                        "prefeito",
                        "vereador");
        assertThat(porId.values())
                .allSatisfy(
                        c -> {
                            assertThat(c.atribuicoes()).hasSizeBetween(1, 3);
                            assertThat(c.urlDescricao())
                                    .isEqualTo("/eleicoes-publicas/cargos#" + c.id());
                        });
        assertThat(porId.get("presidente").sistema()).isEqualTo(Sistema.MAJORITARIO);
        assertThat(porId.get("governador").sistema()).isEqualTo(Sistema.MAJORITARIO);
        assertThat(porId.get("senador").sistema()).isEqualTo(Sistema.MAJORITARIO);
        assertThat(porId.get("prefeito").sistema()).isEqualTo(Sistema.MAJORITARIO);
        assertThat(porId.get("deputado-federal").sistema()).isEqualTo(Sistema.PROPORCIONAL);
        assertThat(porId.get("deputado-estadual").sistema()).isEqualTo(Sistema.PROPORCIONAL);
        assertThat(porId.get("vereador").sistema()).isEqualTo(Sistema.PROPORCIONAL);
    }

    @Test
    @DisplayName("Explica chapas com titular e vice e a diferença entre majoritário e proporcional")
    void secoesDeCargos() {
        // Arrange / Act
        CargosResponse conteudo = service.cargos();

        // Assert
        assertThat(conteudo.secoes())
                .extracting(CargosResponse.Secao::id)
                .containsExactly("chapas", "majoritario-proporcional");
        assertThat(service.cargos().cargos())
                .filteredOn(c -> c.id().equals("prefeito"))
                .singleElement()
                .satisfies(c -> assertThat(c.composicaoChapa()).contains("titular", "vice"));
    }
}
