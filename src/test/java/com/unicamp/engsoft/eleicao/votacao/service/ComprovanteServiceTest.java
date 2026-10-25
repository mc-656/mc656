package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import com.unicamp.engsoft.eleicao.votacao.service.ComprovanteService.DadosComprovante;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critérios de aceitação da RNF-01, sem subir o Spring: a chave é passada direto ao construtor,
 * como o {@code @Value} faria a partir de {@code comprovante.chave}.
 */
class ComprovanteServiceTest {

    private static final String CHAVE = chave("teste-comprovante-chave-32-bytes");
    private static final String OUTRA_CHAVE = chave("outra-chave-de-comprovante-32-by");

    private final ComprovanteService service = new ComprovanteService(CHAVE);

    private final UUID votacaoId = UUID.randomUUID();
    private final UUID cedulaId = UUID.randomUUID();
    private final Instant emitidoEm = Instant.parse("2026-10-21T13:45:12.123456789Z");

    private static String chave(String texto32Bytes) {
        return Base64.getEncoder().encodeToString(texto32Bytes.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytesDe(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    @Test
    @DisplayName("comprovante gerado é verificado e devolve votação, cédula e instante originais")
    void comprovanteGeradoEhVerificado() {
        // Arrange
        String comprovante = service.gerar(votacaoId, cedulaId, emitidoEm);

        // Act
        DadosComprovante dados = service.verificar(comprovante);

        // Assert
        assertThat(dados).isEqualTo(new DadosComprovante(votacaoId, cedulaId, emitidoEm));
    }

    @Test
    @DisplayName("comprovante com qualquer byte alterado é rejeitado")
    void comprovanteAdulteradoEhRejeitado() {
        // Arrange
        byte[] original =
                Base64.getUrlDecoder().decode(service.gerar(votacaoId, cedulaId, emitidoEm));

        // Act / Assert: alterar um único byte, em qualquer posição, invalida o comprovante
        for (int i = 0; i < original.length; i++) {
            byte[] adulterado = original.clone();
            adulterado[i] ^= 0x01;
            String comprovante = Base64.getUrlEncoder().withoutPadding().encodeToString(adulterado);

            assertThatThrownBy(() -> service.verificar(comprovante))
                    .as("byte %d alterado", i)
                    .isInstanceOf(ComprovanteInvalidoException.class);
        }
    }

    @Test
    @DisplayName("comprovante gerado com outra chave é rejeitado")
    void comprovanteDeOutraChaveEhRejeitado() {
        // Arrange
        String comprovante =
                new ComprovanteService(OUTRA_CHAVE).gerar(votacaoId, cedulaId, emitidoEm);

        // Act / Assert
        assertThatThrownBy(() -> service.verificar(comprovante))
                .isInstanceOf(ComprovanteInvalidoException.class);
    }

    @Test
    @DisplayName("comprovante é opaco: não expõe opção votada nem ids em texto claro")
    void comprovanteNaoExpoeDadosEmClaro() {
        // Arrange: a API nem recebe a opção; garantimos também que nada do conteúdo vaza
        UUID opcaoVotada = UUID.randomUUID();

        // Act
        String comprovante = service.gerar(votacaoId, cedulaId, emitidoEm);
        String hexDecodificado =
                HexFormat.of().formatHex(Base64.getUrlDecoder().decode(comprovante));

        // Assert
        for (UUID id : new UUID[] {opcaoVotada, votacaoId, cedulaId}) {
            assertThat(comprovante).doesNotContain(id.toString());
            assertThat(hexDecodificado).doesNotContain(HexFormat.of().formatHex(bytesDe(id)));
        }
    }

    @Test
    @DisplayName("dois comprovantes dos mesmos dados são diferentes (nonce aleatório)")
    void comprovantesDosMesmosDadosSaoDiferentes() {
        // Act
        String primeiro = service.gerar(votacaoId, cedulaId, emitidoEm);
        String segundo = service.gerar(votacaoId, cedulaId, emitidoEm);

        // Assert
        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(service.verificar(segundo)).isEqualTo(service.verificar(primeiro));
    }

    @Test
    @DisplayName("comprovante nulo, fora de Base64 ou truncado é rejeitado")
    void comprovanteMalformadoEhRejeitado() {
        // Arrange
        String valido = service.gerar(votacaoId, cedulaId, emitidoEm);

        // Act / Assert
        for (String invalido :
                new String[] {
                    null, "", "não é base64!", valido.substring(0, valido.length() - 4)
                }) {
            assertThatThrownBy(() -> service.verificar(invalido))
                    .as("comprovante %s", invalido)
                    .isInstanceOf(ComprovanteInvalidoException.class);
        }
    }

    @Test
    @DisplayName(
            "chave ausente, fora de Base64 ou com tamanho diferente de 256 bits impede o start")
    void chaveInvalidaEhRecusada() {
        // Act / Assert
        for (String invalida :
                new String[] {null, " ", "***", chave("curta"), chave("x".repeat(33))}) {
            assertThatThrownBy(() -> new ComprovanteService(invalida))
                    .as("chave %s", invalida)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
