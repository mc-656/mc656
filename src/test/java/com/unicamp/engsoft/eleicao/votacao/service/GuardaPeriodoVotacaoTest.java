package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.*;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuardaPeriodoVotacaoTest {
    private final VotacaoRepository votacoes = mock(VotacaoRepository.class);
    private final GuardaPeriodoVotacao guarda = new GuardaPeriodoVotacao(votacoes);
    private final UUID id = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({
        ",",
        ",2027-01-01T00:00:00Z",
        "2027-01-01T00:00:00Z,",
        "2027-01-01T00:00:00Z,2027-01-01T00:00:00Z",
        "2027-01-02T00:00:00Z,2027-01-01T00:00:00Z"
    })
    void naoPublicaSemPeriodoValido(String inicio, String fim) {
        Votacao v =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        inicio == null ? null : Instant.parse(inicio),
                        fim == null ? null : Instant.parse(fim),
                        "Segundo turno",
                        "Descrição");
        when(votacoes.findById(id)).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> guarda.validar(id)).hasMessageContaining("antes de publicar");
        assertThatThrownBy(() -> v.definirPeriodo(v.getInicioEm(), v.getFimEm()))
                .hasMessageContaining("posterior");
    }

    @Test
    void inexistente() {
        assertThatThrownBy(() -> guarda.validar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
