package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Enquanto as regras não são persistidas (#21), toda votação é tratada como "sem regras
 * configuradas", o mesmo que um rascunho recém-criado. É o lado seguro: sem regra não há voto
 * ponderado, então só o peso 1 é aceito. Quando o PR #97 entrar, passa a devolver {@code
 * Optional.ofNullable(votacao.getRegras())}.
 */
@Component
public class ConsultaRegraVotacaoPadrao implements ConsultaRegraVotacao {

    @Override
    public Optional<RegraVotacao> regraDe(Votacao votacao) {
        return Optional.empty();
    }
}
