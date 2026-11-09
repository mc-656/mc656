package com.unicamp.engsoft.eleicao.votacao.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Voto a registrar (RF-13). O eleitor vem do token, nunca do corpo.
 *
 * @param opcaoId Candidato/chapa escolhido, que precisa pertencer à votação.
 */
public record RegistrarVotoRequest(@NotNull UUID opcaoId) {}
