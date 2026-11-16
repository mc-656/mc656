package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * Procedência de uma informação pública (RF-31/RF-33): de onde veio e quando foi atualizada.
 *
 * <p>É obrigatória em toda entidade do módulo <em>porque</em> o eleitor só pode confiar no perfil
 * se souber a origem de cada dado e se ele está desatualizado (critério da issue #87). A URL é
 * opcional: há fontes sem link estável (ex.: edição impressa de um diário oficial).
 */
@Embeddable
public record Fonte(
        @Column(name = "fonte_nome", nullable = false, length = 150) String nome,
        @Column(name = "fonte_url", length = 500) String url,
        @Column(name = "atualizado_em", nullable = false) Instant atualizadoEm) {}
