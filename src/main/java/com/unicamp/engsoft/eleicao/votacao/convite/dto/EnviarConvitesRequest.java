package com.unicamp.engsoft.eleicao.votacao.convite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lista de endereços para receber o convite por e-mail (RF-17). O limite evita que uma única
 * requisição prenda a thread por minutos enviando milhares de mensagens síncronas.
 */
public record EnviarConvitesRequest(
        @NotEmpty(message = "Informe ao menos um e-mail")
                @Size(max = 200, message = "No máximo 200 e-mails por envio")
                List<
                                @NotBlank(message = "E-mail em branco")
                                @Email(message = "E-mail inválido")
                                @Size(max = 255, message = "E-mail longo demais") String>
                        emails) {}
