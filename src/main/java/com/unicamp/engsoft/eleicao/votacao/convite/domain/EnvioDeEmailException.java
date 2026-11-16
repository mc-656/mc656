package com.unicamp.engsoft.eleicao.votacao.convite.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * O servidor SMTP recusou ou não respondeu ao envio do convite (RF-17). Mapeada para 502: o pedido
 * está certo, quem falhou foi o serviço de e-mail do qual a API depende, e o organizador pode
 * tentar de novo sem mudar nada.
 */
public class EnvioDeEmailException extends DomainException {

    public EnvioDeEmailException(String mensagem, Throwable causa) {
        super(mensagem, HttpStatus.BAD_GATEWAY, causa);
    }
}
