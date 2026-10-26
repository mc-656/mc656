package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Garante que a chave de {@code comprovante.chave} é lida da configuração e o bean sobe. */
class ComprovanteServiceIntegracaoTest extends AbstractIntegrationTest {

    @Autowired private ComprovanteService comprovanteService;

    @Test
    @DisplayName("bean configurado pela aplicação gera e verifica comprovante")
    void beanConfiguradoGeraEVerifica() {
        // Arrange
        UUID votacaoId = UUID.randomUUID();
        UUID cedulaId = UUID.randomUUID();
        Instant agora = Instant.now();

        // Act
        String comprovante = comprovanteService.gerar(votacaoId, cedulaId, agora);

        // Assert
        assertThat(comprovanteService.verificar(comprovante).cedulaId()).isEqualTo(cedulaId);
    }
}
