package com.unicamp.engsoft.eleicao.votacao.convite.service;

import com.unicamp.engsoft.eleicao.votacao.convite.domain.EnvioDeEmailException;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio do convite por e-mail (RF-17) via {@link JavaMailSender}, com o SMTP configurado por
 * variáveis de ambiente ({@code MAIL_HOST}, {@code MAIL_PORT}...). Em desenvolvimento aponta para o
 * Mailpit do {@code compose.yaml}.
 *
 * <p>Uma mensagem por destinatário, e não uma só com todos em cópia: a lista de convidados de uma
 * votação restrita é dado do organizador, e cada eleitor não precisa ver quem mais foi chamado. As
 * mensagens saem num único {@code send(...)}, que reaproveita a conexão SMTP.
 */
@Component
public class EnvioConvitePorEmail {

    private final JavaMailSender mailSender;
    private final String remetente;

    public EnvioConvitePorEmail(
            JavaMailSender mailSender, @Value("${convite.remetente}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    public void enviar(Collection<String> destinatarios, String nomeVotacao, String link) {
        SimpleMailMessage[] mensagens =
                destinatarios.stream()
                        .map(destinatario -> mensagem(destinatario, nomeVotacao, link))
                        .toArray(SimpleMailMessage[]::new);
        try {
            mailSender.send(mensagens);
        } catch (MailException e) {
            throw new EnvioDeEmailException(
                    "Não foi possível enviar os convites por e-mail; tente novamente", e);
        }
    }

    private SimpleMailMessage mensagem(String destinatario, String nomeVotacao, String link) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject("Convite para a votação \"%s\"".formatted(nomeVotacao));
        mensagem.setText(
                ("Olá!%n%n"
                                + "Você foi convidado(a) para participar da votação \"%s\".%n%n"
                                + "Para entrar como eleitor(a), abra o link abaixo. Se ainda não tiver conta, "
                                + "faça o cadastro e você voltará ao convite em seguida:%n%n"
                                + "%s%n")
                        .formatted(nomeVotacao, link));
        return mensagem;
    }
}
