package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unicamp.engsoft.eleicao.shared.exception.ComprovanteInvalidoException;
import com.unicamp.engsoft.eleicao.votacao.service.ComprovanteService.DadosComprovante;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Critérios de aceitação da RNF-01, sem subir o Spring: a chave é passada direto ao construtor,
 * como o {@code @Value} faria a partir de {@code comprovante.chave}.
 *
 * <p>Organizado por classes de equivalência (#152). Em {@code verificar}, as entradas são presença,
 * codificação, tamanho decodificado (73 bytes), byte de versão e autenticidade da tag GCM; no
 * construtor, a chave. Cada teste traz no {@code @DisplayName} o ID da classe:
 *
 * <ul>
 *   <li>CE-V1: comprovante gerado com a mesma chave;
 *   <li>CE-V2: idem, com espaços ou quebras de linha em volta;
 *   <li>CE-V3: chave Base64 de exatamente 32 bytes;
 *   <li>CE-I1: comprovante nulo;
 *   <li>CE-I2: caracteres fora do alfabeto Base64url;
 *   <li>CE-I3: Base64url válido com tamanho diferente de 73 bytes;
 *   <li>CE-I4: 73 bytes com versão diferente de 1;
 *   <li>CE-I5: 73 bytes, versão 1, conteúdo alterado;
 *   <li>CE-I6: comprovante gerado com outra chave;
 *   <li>CE-I7: chave nula ou em branco;
 *   <li>CE-I8: chave fora de Base64;
 *   <li>CE-I9: chave Base64 com tamanho diferente de 32 bytes.
 * </ul>
 *
 * <p>Os tamanhos exatos (73 bytes no comprovante, 32 na chave) também são testados nos vizinhos
 * imediatos, em {@link ValorLimite}.
 */
class ComprovanteServiceTest {

    private static final int TAMANHO_COMPROVANTE = 73;

    private static final String CHAVE = chave("teste-comprovante-chave-32-bytes");
    private static final String OUTRA_CHAVE = chave("outra-chave-de-comprovante-32-by");

    private final ComprovanteService service = new ComprovanteService(CHAVE);

    private final UUID votacaoId = UUID.randomUUID();
    private final UUID cedulaId = UUID.randomUUID();
    private final Instant emitidoEm = Instant.parse("2026-10-21T13:45:12.123456789Z");

    private static String chave(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytesDe(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] bytesValidos() {
        return Base64.getUrlDecoder().decode(service.gerar(votacaoId, cedulaId, emitidoEm));
    }

    private void assertRejeitado(String comprovante) {
        assertThatThrownBy(() -> service.verificar(comprovante))
                .isInstanceOf(ComprovanteInvalidoException.class);
    }

    @Nested
    @DisplayName("verificar: classes válidas")
    class VerificarValidas {

        @Test
        @DisplayName("CE-V1: comprovante gerado é verificado e devolve os dados originais")
        void comprovanteGeradoEhVerificado() {
            // Arrange
            String comprovante = service.gerar(votacaoId, cedulaId, emitidoEm);

            // Act
            DadosComprovante dados = service.verificar(comprovante);

            // Assert
            assertThat(dados).isEqualTo(new DadosComprovante(votacaoId, cedulaId, emitidoEm));
        }

        @Test
        @DisplayName("CE-V1: dois comprovantes dos mesmos dados diferem e atestam o mesmo conteúdo")
        void comprovantesDosMesmosDadosSaoDiferentes() {
            // Act
            String primeiro = service.gerar(votacaoId, cedulaId, emitidoEm);
            String segundo = service.gerar(votacaoId, cedulaId, emitidoEm);

            // Assert
            assertThat(primeiro).isNotEqualTo(segundo);
            assertThat(service.verificar(segundo)).isEqualTo(service.verificar(primeiro));
        }

        @Test
        @DisplayName("CE-V1: comprovante é opaco e não expõe opção votada nem ids em claro")
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

        @ParameterizedTest(name = "envolto por [{0}]")
        @ValueSource(strings = {" ", "\n", "\t ", " \r\n"})
        @DisplayName(
                "CE-V2: espaços e quebras de linha em volta, como ao colar o texto, são ignorados")
        void comprovanteComEspacosEmVolta(String espaco) {
            // Arrange
            String comprovante = espaco + service.gerar(votacaoId, cedulaId, emitidoEm) + espaco;

            // Act
            DadosComprovante dados = service.verificar(comprovante);

            // Assert
            assertThat(dados.cedulaId()).isEqualTo(cedulaId);
        }
    }

    @Nested
    @DisplayName("verificar: classes inválidas")
    class VerificarInvalidas {

        @Test
        @DisplayName("CE-I1: comprovante nulo é rejeitado")
        void comprovanteNulo() {
            assertRejeitado(null);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"não é base64!", "abc$def", "a b"})
        @DisplayName("CE-I2: caracteres fora do alfabeto Base64url são rejeitados")
        void foraDoAlfabeto(String comprovante) {
            assertRejeitado(comprovante);
        }

        @Test
        @DisplayName("CE-I2: comprovante reescrito no alfabeto Base64 padrão (+ e /) é rejeitado")
        void alfabetoPadrao() {
            // Arrange: gera até sair um comprovante com '-' ou '_', que mudam no alfabeto padrão
            String urlSafe;
            do {
                urlSafe = service.gerar(votacaoId, cedulaId, emitidoEm);
            } while (urlSafe.indexOf('-') < 0 && urlSafe.indexOf('_') < 0);
            String padrao = urlSafe.replace('-', '+').replace('_', '/');

            // Act + Assert
            assertRejeitado(padrao);
        }

        @Test
        @DisplayName("CE-I3: Base64url válido com tamanho bem diferente de 73 bytes é rejeitado")
        void tamanhoErrado() {
            // Arrange
            String valido = service.gerar(votacaoId, cedulaId, emitidoEm);

            // Act + Assert
            assertRejeitado("");
            assertRejeitado(base64url(new byte[16]));
            assertRejeitado(valido.substring(0, valido.length() - 4));
            assertRejeitado(valido + valido);
        }

        @ParameterizedTest(name = "versão {0}")
        @ValueSource(bytes = {0, 2, -1})
        @DisplayName("CE-I4: comprovante com versão diferente de 1 é rejeitado")
        void versaoDiferente(byte versao) {
            // Arrange
            byte[] bytes = bytesValidos();
            bytes[0] = versao;

            // Act + Assert
            assertRejeitado(base64url(bytes));
        }

        @Test
        @DisplayName("CE-I5: qualquer byte alterado depois da versão invalida o comprovante")
        void comprovanteAdulterado() {
            // Arrange
            byte[] original = bytesValidos();

            // Act + Assert: do nonce à tag, alterar um único bit invalida o comprovante
            for (int i = 1; i < original.length; i++) {
                byte[] adulterado = original.clone();
                adulterado[i] ^= 0x01;

                assertThatThrownBy(() -> service.verificar(base64url(adulterado)))
                        .as("byte %d alterado", i)
                        .isInstanceOf(ComprovanteInvalidoException.class);
            }
        }

        @Test
        @DisplayName("CE-I6: comprovante gerado com outra chave é rejeitado")
        void comprovanteDeOutraChave() {
            // Arrange
            String comprovante =
                    new ComprovanteService(OUTRA_CHAVE).gerar(votacaoId, cedulaId, emitidoEm);

            // Act + Assert
            assertRejeitado(comprovante);
        }
    }

    @Nested
    @DisplayName("Construtor: chave")
    class Chave {

        @Test
        @DisplayName("CE-V3: chave Base64 de 32 bytes, mesmo com espaços em volta, é aceita")
        void chaveValida() {
            // Act
            ComprovanteService comEspacos = new ComprovanteService("  " + CHAVE + "\n");

            // Assert: a mesma chave, então verifica o que o serviço principal gerou
            assertThat(comEspacos.verificar(service.gerar(votacaoId, cedulaId, emitidoEm)))
                    .isEqualTo(new DadosComprovante(votacaoId, cedulaId, emitidoEm));
        }

        @ParameterizedTest(name = "[{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t\n"})
        @DisplayName("CE-I7: chave nula ou em branco impede o start")
        void chaveAusente(String chave) {
            assertThatThrownBy(() -> new ComprovanteService(chave))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("não configurada");
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"***", "chave com espaço no meio", "abc$"})
        @DisplayName("CE-I8: chave fora de Base64 impede o start")
        void chaveForaDeBase64(String chave) {
            assertThatThrownBy(() -> new ComprovanteService(chave))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Base64");
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {1, 16, 64})
        @DisplayName("CE-I9: chave Base64 com tamanho diferente de 32 bytes impede o start")
        void chaveComTamanhoErrado(int tamanho) {
            assertThatThrownBy(() -> new ComprovanteService(chave("x".repeat(tamanho))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32 bytes");
        }
    }

    /**
     * Vizinhos dos dois tamanhos fixos. No comprovante, o caso de 73 bytes é o próprio comprovante
     * válido; os de 72 e 74 partem dele, removendo o último byte da tag ou acrescentando um.
     */
    @Nested
    @DisplayName("Análise de valor limite")
    class ValorLimite {

        @Test
        @DisplayName("AVL comprovante: 73 bytes é aceito")
        void comprovanteNoTamanho() {
            // Arrange
            byte[] bytes = bytesValidos();

            // Act + Assert
            assertThat(bytes).hasSize(TAMANHO_COMPROVANTE);
            assertThat(service.verificar(base64url(bytes)).votacaoId()).isEqualTo(votacaoId);
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {TAMANHO_COMPROVANTE - 1, TAMANHO_COMPROVANTE + 1})
        @DisplayName("AVL comprovante: 72 e 74 bytes são rejeitados")
        void comprovanteVizinhos(int tamanho) {
            // Arrange
            byte[] bytes = Arrays.copyOf(bytesValidos(), tamanho);

            // Act + Assert
            assertRejeitado(base64url(bytes));
        }

        @Test
        @DisplayName("AVL chave: 32 bytes é aceita")
        void chaveNoTamanho() {
            // Act
            ComprovanteService servico = new ComprovanteService(chave("x".repeat(32)));

            // Assert
            assertThat(servico.verificar(servico.gerar(votacaoId, cedulaId, emitidoEm)))
                    .isEqualTo(new DadosComprovante(votacaoId, cedulaId, emitidoEm));
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {31, 33})
        @DisplayName("AVL chave: 31 e 33 bytes impedem o start")
        void chaveVizinhos(int tamanho) {
            assertThatThrownBy(() -> new ComprovanteService(chave("x".repeat(tamanho))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32 bytes");
        }
    }
}
