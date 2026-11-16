package com.unicamp.engsoft.eleicao.votacao.convite.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeradorQrCodeTest {

    private final GeradorQrCode gerador = new GeradorQrCode();

    @Test
    @DisplayName("Gera PNG de QR Code legível com PURE_BARCODE")
    void geraQrCodeLegivel() throws Exception {
        String url = "http://localhost:8080/convites/token-de-teste-123456789";

        byte[] png = gerador.png(url);
        assertThat(png).isNotEmpty();

        String lido =
                new QRCodeReader()
                        .decode(
                                new BinaryBitmap(
                                        new HybridBinarizer(
                                                new BufferedImageLuminanceSource(
                                                        ImageIO.read(
                                                                new ByteArrayInputStream(png))))),
                                Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE))
                        .getText();
        assertThat(lido).isEqualTo(url);
    }
}
