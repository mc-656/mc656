package com.unicamp.engsoft.eleicao.votacao.controller;

import com.unicamp.engsoft.eleicao.votacao.dto.ComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.RegistrarVotoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificacaoComprovanteResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VerificarComprovanteRequest;
import com.unicamp.engsoft.eleicao.votacao.service.RegistroVotoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voto (RF-13/RF-14) e verificação de comprovante (RNF-01).
 *
 * <p>Não existe, de propósito, nenhuma rota de leitura, alteração ou remoção de voto: o voto é
 * append-only (SPECS §7.3) e nem o {@code ADMIN_VOTACAO} vê voto individual (RNF-02); ele só terá
 * acesso a agregados na apuração (#50).
 *
 * <p>O voto não usa {@code @PreAuthorize("@autorizacaoVotacao.ehEleitor(...)")}: a elegibilidade é
 * checada no service pelo {@code ElegibilidadeService}, que diferencia votação inexistente (404) de
 * usuário não elegível (403) em vez de responder só "negado".
 */
@RestController
public class VotoController {

    private final RegistroVotoService registroVotoService;

    public VotoController(RegistroVotoService registroVotoService) {
        this.registroVotoService = registroVotoService;
    }

    @PostMapping("/api/votacoes/{votacaoId}/votos")
    @ResponseStatus(HttpStatus.CREATED)
    public ComprovanteResponse votar(
            @PathVariable UUID votacaoId,
            @RequestBody @Valid RegistrarVotoRequest request,
            Authentication autenticacao) {
        return registroVotoService.registrar(votacaoId, request.opcaoId(), autenticacao);
    }

    /**
     * Qualquer usuário autenticado pode verificar: o comprovante já é a prova de posse, e a
     * resposta não revela a opção votada.
     */
    @PostMapping("/api/comprovantes/verificacao")
    public VerificacaoComprovanteResponse verificar(
            @RequestBody @Valid VerificarComprovanteRequest request) {
        return registroVotoService.verificar(request.comprovante());
    }
}
