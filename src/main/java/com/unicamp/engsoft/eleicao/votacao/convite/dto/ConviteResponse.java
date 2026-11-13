package com.unicamp.engsoft.eleicao.votacao.convite.dto;

import com.unicamp.engsoft.eleicao.votacao.convite.domain.ConviteVotacao;
import com.unicamp.engsoft.eleicao.votacao.convite.domain.ModoConvite;
import java.util.List;
import java.util.UUID;

/**
 * Convite como o organizador o vê (RF-17): o link pronto para colar no WhatsApp, o caminho do QR
 * Code e a lista de e-mails convidados. Só o {@code ADMIN_VOTACAO} recebe esta resposta.
 */
public record ConviteResponse(
        UUID votacaoId,
        String token,
        String link,
        String qrCode,
        ModoConvite modo,
        List<String> emails) {

    public static ConviteResponse of(ConviteVotacao convite, String link) {
        return new ConviteResponse(
                convite.getVotacaoId(),
                convite.getToken(),
                link,
                "/api/votacoes/%s/convite/qrcode".formatted(convite.getVotacaoId()),
                convite.getModo(),
                convite.getEmails().stream().sorted().toList());
    }
}
