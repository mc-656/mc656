package com.unicamp.engsoft.eleicao.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "O email é obrigatório.") @Email(message = "O email deve ser válido.")
                String email,
        @NotBlank(message = "A senha é obrigatória") String senha) {}
