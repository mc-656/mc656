package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CadastrarCandidatoRequest(
        @NotBlank @Size(max = 100) String nome,
        @Size(max = 255) String descricao,
        @Past LocalDate dataNascimento) {
    public CadastrarCandidatoRequest(String nome, String descricao) {
        this(nome, descricao, null);
    }
}
