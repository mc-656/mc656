package com.unicamp.engsoft.eleicao.votacao.domain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao ;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao ;


class VotacaoTest {

    @Test
    @DisplayName("Deve criar uma votação válida com o estado inicial em RascunhoVotacao")
    void deveCriarVotacaoComSucesso() {
        // Arrange
        TipoVotacao tipoEsperado = TipoVotacao.CONSULTA; // Ajuste para um enum válido do seu projeto
        Instant inicio = Instant.now();
        Instant fim = inicio.plusSeconds(3600);

        // Act
        Votacao votacao = new Votacao(tipoEsperado, inicio, fim);

        // Assert
        assertNotNull(votacao);
        assertEquals(tipoEsperado, votacao.getTipo());
        assertEquals(inicio, votacao.getInicioEm());
        assertEquals(fim, votacao.getFimEm());
        
        // Verifica se o estado inicial predefinido no atributo foi atribuído corretamente
        assertEquals(EstadoVotacao.RascunhoVotacao, votacao.getEstado());
        
        // O ID deve ser nulo antes da persistência no banco (gerado pelo JPA)
        assertNull(votacao.getId());
    }

    @Test
    @DisplayName("Deve permitir a instanciação pelo construtor protegido para o JPA")
    void deveInstanciarPeloConstrutorProtegido() {
        // Act
        Votacao votacao = new Votacao();

        // Assert
        assertNotNull(votacao);
        assertNull(votacao.getId());
        assertNull(votacao.getTipo());
        assertNull(votacao.getInicioEm());
        assertNull(votacao.getFimEm());
        assertEquals(EstadoVotacao.RascunhoVotacao, votacao.getEstado());
    }
}
