package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Identificador didático do cargo (link para RF-32)")
class CargoPublicoTest {

    @ParameterizedTest(name = "\"{0}\" vira \"{1}\"")
    @CsvSource({
        "Prefeito, prefeito",
        "Deputado Federal, deputado-federal",
        "' Vice-Prefeito ', vice-prefeito",
        "Senador da República, senador-da-republica"
    })
    void idDidaticoSegueConvencaoDoConteudoDidatico(String nome, String esperado) {
        // Arrange
        CargoPublico cargo = new CargoPublico();
        ReflectionTestUtils.setField(cargo, "nome", nome);

        // Act
        String id = cargo.idDidatico();

        // Assert
        assertThat(id).isEqualTo(esperado);
    }
}
