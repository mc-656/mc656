package com.unicamp.engsoft.eleicao.votacao.convite.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * QR Code em PNG de um texto (o link do convite, RF-17), com ZXing — biblioteca livre, sem serviço
 * externo: o link (que é a credencial do convite) não sai do servidor para ser desenhado.
 *
 * <p>Correção de erro {@code M} (~15%) aguenta foto torta de projetor em reunião presencial sem
 * deixar o código denso demais para um link de ~80 caracteres.
 */
@Component
public class GeradorQrCode {

    static final int LADO_PX = 320;

    public byte[] png(String conteudo) {
        try {
            BitMatrix matriz =
                    new QRCodeWriter()
                            .encode(
                                    conteudo,
                                    BarcodeFormat.QR_CODE,
                                    LADO_PX,
                                    LADO_PX,
                                    Map.of(
                                            EncodeHintType.ERROR_CORRECTION,
                                            ErrorCorrectionLevel.M,
                                            EncodeHintType.CHARACTER_SET,
                                            StandardCharsets.UTF_8.name(),
                                            EncodeHintType.MARGIN,
                                            2));
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", saida);
            return saida.toByteArray();
        } catch (WriterException e) {
            // Só acontece com conteúdo além da capacidade do QR Code, o que um link não atinge.
            throw new IllegalStateException("Não foi possível gerar o QR Code do convite", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
