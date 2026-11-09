package com.unicamp.engsoft.eleicao.votacao.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Comprovante entregue ao eleitor ao votar (RF-13, RNF-01). Não traz a opção votada nem o id da
 * cédula (RNF-02): o comprovante só serve para provar que o voto foi registrado.
 *
 * @param votacaoId Votação em que o voto foi registrado.
 * @param comprovante Token opaco (AES-GCM, Base64url) a apresentar na verificação.
 * @param emitidoEm Instante de emissão, que só existe aqui e dentro do comprovante.
 */
public record ComprovanteResponse(UUID votacaoId, String comprovante, Instant emitidoEm) {}
