package com.unicamp.engsoft.eleicao.votacao.convite.controller;

import com.unicamp.engsoft.eleicao.votacao.convite.dto.ConviteResponse;
import com.unicamp.engsoft.eleicao.votacao.convite.dto.EnviarConvitesRequest;
import com.unicamp.engsoft.eleicao.votacao.convite.dto.GerarConviteRequest;
import com.unicamp.engsoft.eleicao.votacao.convite.service.ConviteService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convite da votação pelo lado do organizador (RF-17): link, QR Code e envio por e-mail. Tudo é só
 * para {@code ADMIN_VOTACAO}: o link é a credencial de entrada, então quem o vê decide quem entra.
 */
@RestController
@RequestMapping("/api/votacoes/{votacaoId}/convite")
public class ConviteController {

    private final ConviteService conviteService;

    public ConviteController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    /** Cria o convite ou troca o modo; idempotente, por isso {@code PUT}. */
    @PutMapping
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public ConviteResponse gerar(
            @PathVariable UUID votacaoId, @RequestBody @Valid GerarConviteRequest request) {
        return conviteService.gerar(votacaoId, request.modo());
    }

    @GetMapping
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public ConviteResponse consultar(@PathVariable UUID votacaoId) {
        return conviteService.consultar(votacaoId);
    }

    /**
     * PNG do QR Code do link. {@code no-store} porque a imagem carrega a credencial do convite e
     * não deve ficar em cache compartilhado.
     */
    @GetMapping(value = "/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public ResponseEntity<byte[]> qrCode(@PathVariable UUID votacaoId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(conviteService.qrCode(votacaoId));
    }

    @PostMapping("/emails")
    @PreAuthorize("@autorizacaoVotacao.ehAdmin(#votacaoId, authentication)")
    public ConviteResponse enviarPorEmail(
            @PathVariable UUID votacaoId, @RequestBody @Valid EnviarConvitesRequest request) {
        return conviteService.enviarPorEmail(votacaoId, request.emails());
    }
}
