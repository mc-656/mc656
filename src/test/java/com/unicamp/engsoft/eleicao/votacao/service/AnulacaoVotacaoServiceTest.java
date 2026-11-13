package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Os dois caminhos da anulação (RF-42, #53) delegam ao state service com a origem certa. */
@ExtendWith(MockitoExtension.class)
class AnulacaoVotacaoServiceTest {

    private static final UUID VOTACAO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    @Mock VotacaoStateService stateService;

    private AnulacaoVotacaoService service;

    @BeforeEach
    void configura() {
        service = new AnulacaoVotacaoService(stateService);
    }

    @Test
    @DisplayName(
            "Falta de quórum anula com origem SISTEMA, sem autor e motivo 'quórum não atingido'")
    void faltaDeQuorumEhAutomatica() {
        // Act
        service.anularPorFaltaDeQuorum(VOTACAO);

        // Assert
        verify(stateService).transitar(VOTACAO, EstadoVotacao.Anulada, null, "quórum não atingido");
    }

    @Test
    @DisplayName("Irregularidade anula com o admin como autor e a justificativa informada")
    void irregularidadeEhManual() {
        // Act
        service.anularPorIrregularidade(VOTACAO, ADMIN, "Urna com votos duplicados");

        // Assert
        verify(stateService)
                .transitar(VOTACAO, EstadoVotacao.Anulada, ADMIN, "Urna com votos duplicados");
    }

    @Test
    @DisplayName("Anulação por irregularidade sem autor é recusada, para não virar origem SISTEMA")
    void irregularidadeSemAutor() {
        // Act + Assert
        assertThatThrownBy(() -> service.anularPorIrregularidade(VOTACAO, null, "fraude"))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(stateService);
    }
}
