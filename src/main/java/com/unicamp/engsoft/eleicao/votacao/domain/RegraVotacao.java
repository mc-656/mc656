package com.unicamp.engsoft.eleicao.votacao.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;

/**
 * Value object com as regras de apuração de uma votação (RF-11).
 *
 * <p>Os percentuais usam a escala de 0 a 100. A semântica de cada {@link TipoMaioria}:
 *
 * <ul>
 *   <li>{@code MAIORIA_SIMPLES}: vence o mais votado;
 *   <li>{@code MAIORIA_ABSOLUTA}: vence quem tiver mais de 50% dos votos válidos (ponderados);
 *   <li>{@code MAIORIA_QUALIFICADA}: vence quem atingir {@code percentualQualificado} dos votos
 *       válidos (ponderados).
 * </ul>
 *
 * <p>Os pesos de cada eleitor não ficam aqui: são guardados no papel {@code ELEITOR} da votação.
 *
 * @param tipoMaioria Tipo de maioria exigida. Obrigatório.
 * @param percentualQualificado Percentual exigido na maioria qualificada, maior que 50 e no máximo
 *     100. Deve ser {@code null} nos demais tipos.
 * @param segundoTurno Indica se a votação prevê segundo turno. Só permitido com maioria absoluta ou
 *     qualificada, já que na maioria simples sempre há um mais votado.
 * @param quorumMinimoPercentual Percentual mínimo de eleitores aptos que precisam votar, entre 0 e
 *     100. {@code null} indica votação sem quórum.
 * @param votoPonderado Indica se os votos usam o peso configurado para cada eleitor.
 * @throws DadosInvalidosException Se a combinação de parâmetros for inválida.
 */
public record RegraVotacao(
        TipoMaioria tipoMaioria,
        BigDecimal percentualQualificado,
        boolean segundoTurno,
        BigDecimal quorumMinimoPercentual,
        boolean votoPonderado) {

    private static final BigDecimal CINQUENTA = BigDecimal.valueOf(50);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public RegraVotacao {
        if (tipoMaioria == null) {
            throw new DadosInvalidosException("O tipo de maioria é obrigatório");
        }
        validarPercentualQualificado(tipoMaioria, percentualQualificado);
        if (segundoTurno && tipoMaioria == TipoMaioria.MAIORIA_SIMPLES) {
            throw new DadosInvalidosException(
                    "O segundo turno exige maioria absoluta ou qualificada");
        }
        if (quorumMinimoPercentual != null
                && (quorumMinimoPercentual.signum() < 0
                        || quorumMinimoPercentual.compareTo(CEM) > 0)) {
            throw new DadosInvalidosException("O quórum mínimo deve estar entre 0 e 100");
        }
    }

    private static void validarPercentualQualificado(
            TipoMaioria tipoMaioria, BigDecimal percentualQualificado) {
        if (tipoMaioria != TipoMaioria.MAIORIA_QUALIFICADA) {
            if (percentualQualificado != null) {
                throw new DadosInvalidosException(
                        "O percentual qualificado só pode ser definido para maioria qualificada");
            }
            return;
        }
        if (percentualQualificado == null) {
            throw new DadosInvalidosException("A maioria qualificada exige percentual definido");
        }
        if (percentualQualificado.compareTo(CINQUENTA) <= 0
                || percentualQualificado.compareTo(CEM) > 0) {
            throw new DadosInvalidosException(
                    "O percentual qualificado deve ser maior que 50 e no máximo 100");
        }
    }
}
