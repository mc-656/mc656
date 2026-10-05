package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastrarCandidatoRequest(
        @NotBlank @Size(max = 100) String nome, @Size(max = 255) String descricao) {}
