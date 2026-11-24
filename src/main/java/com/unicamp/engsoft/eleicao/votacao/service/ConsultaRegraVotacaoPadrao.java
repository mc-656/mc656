package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Consulta as regras persistidas na {@link Votacao}. Quando ainda não configuradas (rascunho
 * recém-criado), devolve vazio: sem regra não há voto ponderado, então só o peso 1 é aceito.
 */
@Component
public class ConsultaRegraVotacaoPadrao implements ConsultaRegraVotacao {

    @Override
    public Optional<RegraVotacao> regraDe(Votacao votacao) {
        return Optional.ofNullable(votacao).map(Votacao::getRegras);
    }
}
