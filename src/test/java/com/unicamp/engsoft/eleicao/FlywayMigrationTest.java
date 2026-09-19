package com.unicamp.engsoft.eleicao;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaTodasAsMigracoesEmBancoVazio() {
        assertThat(flyway.info().applied()).isNotEmpty();

        Integer falhas = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertThat(falhas).isZero();
    }

    @Test
    void rodarNovamenteNaoReaplicaNemQuebra() {
        int antes = flyway.info().applied().length;

        flyway.migrate();   // segunda execução sobre o mesmo banco
        flyway.validate();  // falha se checksum divergir

        assertThat(flyway.info().applied()).hasSize(antes);
    }
}