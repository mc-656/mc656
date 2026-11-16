package com.unicamp.engsoft.eleicao.votacao.dto;

import com.unicamp.engsoft.eleicao.votacao.domain.ModeloVotacao;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Criação de votação a partir de um modelo pronto (RF-16). O organizador informa só título, opções
 * e período; as regras vêm do {@link ModeloVotacao}.
 *
 * <p>Os limites de tamanho são os mesmos de {@link CriarVotacaoRequest} e {@link
 * CadastrarCandidatoRequest}, porque a criação é feita reaproveitando esses fluxos.
 *
 * @param descricao opcional; se ausente, usa a descrição do modelo (a coluna é obrigatória).
 * @param opcoes candidatos/opções; se ausente ou vazia, usa {@link ModeloVotacao#opcoesPadrao()}
 *     (ex.: "Sim"/"Não" na decisão sim/não).
 */
public record CriarVotacaoPorModeloRequest(
        @NotNull(message = "O modelo é obrigatório") ModeloVotacao modelo,
        @NotBlank @Size(max = 50) String nome,
        @Size(max = 100) String descricao,
        @FutureOrPresent(message = "A data de início não pode estar no passado") @NotNull
                Instant inicioEm,
        @NotNull Instant fimEm,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> opcoes) {}
