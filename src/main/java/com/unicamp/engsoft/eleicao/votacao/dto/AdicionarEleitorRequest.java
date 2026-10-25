package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Eleitor a adicionar numa votação (RF-10). O usuário é identificado pelo e-mail, que é o que o
 * administrador conhece, e não pelo id interno.
 *
 * @param email E-mail de um usuário já cadastrado.
 * @param peso Peso do voto; {@code null} vale 1. A validação fica no service porque depende da
 *     regra da votação.
 */
public record AdicionarEleitorRequest(@NotBlank @Email String email, BigDecimal peso) {}
