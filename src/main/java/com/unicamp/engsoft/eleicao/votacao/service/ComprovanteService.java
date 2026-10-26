package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gera e verifica o comprovante criptografado entregue ao eleitor ao registrar um voto (RF-13,
 * RNF-01).
 *
 * <p><b>Algoritmo.</b> AES-256-GCM em vez de HMAC: o GCM autentica (qualquer byte alterado ou chave
 * diferente faz a tag falhar) e, além disso, deixa o conteúdo opaco. Com HMAC os ids de votação e
 * de cédula iriam em claro no comprovante; com GCM só o servidor consegue lê-los. O nonce de 96
 * bits é aleatório por comprovante, então dois comprovantes com os mesmos dados nunca são iguais.
 *
 * <p><b>Sigilo (RNF-02).</b> A opção votada nem entra no comprovante: ele prova que <em>uma</em>
 * cédula foi registrada naquela votação, não <em>qual</em> opção ela contém. Assim o comprovante
 * não serve para coação ou venda de voto, mesmo que o eleitor o mostre a terceiros.
 *
 * <p><b>Timestamp.</b> O instante de emissão vai só no comprovante e nunca é persistido junto da
 * {@code Cedula}: se a cédula guardasse o horário, bastaria cruzá-lo com o horário da {@code
 * Participacao} para descobrir quem votou em quê. Por isso este serviço não tem estado nem
 * persistência; quem registra o voto (#48) chama {@link #gerar} e devolve o resultado ao eleitor.
 *
 * <p><b>Formato.</b> Base64url sem padding de {@code versão(1) || nonce(12) || cifra(44) ||
 * tag(16)}. O byte de versão entra como dado autenticado (AAD), permitindo trocar o formato no
 * futuro sem ambiguidade.
 */
@Service
public class ComprovanteService {

    private static final byte VERSAO = 1;
    private static final int TAMANHO_CHAVE = 32;
    private static final int TAMANHO_NONCE = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_PAYLOAD = 16 + 16 + 8 + 4;
    private static final int INICIO_CIFRA = 1 + TAMANHO_NONCE;
    private static final int TAMANHO_COMPROVANTE =
            INICIO_CIFRA + TAMANHO_PAYLOAD + TAMANHO_TAG_BITS / 8;
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";

    private final SecretKey chave;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * @param chaveBase64 chave AES de 256 bits em Base64, vinda de {@code comprovante.chave}
     *     (variável {@code COMPROVANTE_CHAVE}). Exigir exatamente 32 bytes, em vez de derivar de
     *     uma senha qualquer, evita que uma chave fraca passe despercebida: o contexto nem sobe.
     */
    public ComprovanteService(@Value("${comprovante.chave}") String chaveBase64) {
        if (chaveBase64 == null || chaveBase64.isBlank()) {
            throw new IllegalStateException("comprovante.chave não configurada.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chaveBase64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("comprovante.chave deve estar em Base64.", e);
        }
        if (bytes.length != TAMANHO_CHAVE) {
            throw new IllegalStateException(
                    "comprovante.chave deve ter exatamente 32 bytes (256 bits) em Base64.");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    /** Gera o comprovante de uma cédula registrada. Não recebe a opção votada de propósito. */
    public String gerar(UUID votacaoId, UUID cedulaId, Instant emitidoEm) {
        Objects.requireNonNull(votacaoId, "votacaoId");
        Objects.requireNonNull(cedulaId, "cedulaId");
        Objects.requireNonNull(emitidoEm, "emitidoEm");

        ByteBuffer payload = ByteBuffer.allocate(TAMANHO_PAYLOAD);
        payload.putLong(votacaoId.getMostSignificantBits())
                .putLong(votacaoId.getLeastSignificantBits())
                .putLong(cedulaId.getMostSignificantBits())
                .putLong(cedulaId.getLeastSignificantBits())
                .putLong(emitidoEm.getEpochSecond())
                .putInt(emitidoEm.getNano());

        byte[] nonce = new byte[TAMANHO_NONCE];
        aleatorio.nextBytes(nonce);

        byte[] cifra;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            cipher.updateAAD(new byte[] {VERSAO});
            cifra = cipher.doFinal(payload.array());
        } catch (GeneralSecurityException e) {
            // AES-GCM é obrigatório em toda JVM; chegar aqui é erro de ambiente, não do eleitor.
            throw new IllegalStateException("Falha ao gerar comprovante de voto.", e);
        }

        ByteBuffer saida = ByteBuffer.allocate(TAMANHO_COMPROVANTE);
        saida.put(VERSAO).put(nonce).put(cifra);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(saida.array());
    }

    /**
     * Verifica um comprovante apresentado pelo eleitor e devolve os dados que ele atesta.
     *
     * @throws ComprovanteInvalidoException se o comprovante estiver malformado, adulterado ou tiver
     *     sido gerado com outra chave
     */
    public DadosComprovante verificar(String comprovante) {
        if (comprovante == null) {
            throw new ComprovanteInvalidoException();
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(comprovante.strip());
        } catch (IllegalArgumentException e) {
            throw new ComprovanteInvalidoException(e);
        }
        if (bytes.length != TAMANHO_COMPROVANTE || bytes[0] != VERSAO) {
            throw new ComprovanteInvalidoException();
        }

        byte[] claro;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    chave,
                    new GCMParameterSpec(TAMANHO_TAG_BITS, bytes, 1, TAMANHO_NONCE));
            cipher.updateAAD(bytes, 0, 1);
            claro = cipher.doFinal(bytes, INICIO_CIFRA, bytes.length - INICIO_CIFRA);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException: adulterado ou outra chave, indistinguíveis de propósito.
            throw new ComprovanteInvalidoException(e);
        }

        ByteBuffer payload = ByteBuffer.wrap(claro);
        UUID votacaoId = new UUID(payload.getLong(), payload.getLong());
        UUID cedulaId = new UUID(payload.getLong(), payload.getLong());
        long segundos = payload.getLong();
        int nanos = payload.getInt();
        return new DadosComprovante(votacaoId, cedulaId, Instant.ofEpochSecond(segundos, nanos));
    }

    /** Conteúdo atestado por um comprovante válido. Nunca inclui a opção votada (RNF-02). */
    public record DadosComprovante(UUID votacaoId, UUID cedulaId, Instant emitidoEm) {}
}
