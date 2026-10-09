package com.unicamp.engsoft.eleicao.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Relógio da aplicação e agendamento das transições por data (#24).
 *
 * <p>O {@link Clock} é bean para que testes fixem o "agora" sem depender da hora real.
 */
@Configuration
public class AgendamentoConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Separado do restante para poder ser desligado: nos testes o job rodando sozinho alteraria
     * votações no meio de outros casos. Os testes do job chamam o método diretamente.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(
            name = "votacao.agendador.habilitado",
            havingValue = "true",
            matchIfMissing = true)
    static class Agendador {}
}
