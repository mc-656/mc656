package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.ComportamentosDeEstado;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto em que um service pergunta "posso mexer nesta votação?" (RF-11, RF-12, RF-10).
 *
 * <p>Antes, a regra "só se mexe em rascunho" existia três vezes: em {@code VotacaoService} e em
 * {@code CandidatoOuChapaService} como {@code findById} + comparação, sem lock, e em {@code
 * EleitorService} com lock. O lock de escrita ({@link VotacaoRepository#buscarParaTransicao}) é o
 * que fecha a corrida: sem ele, a votação poderia ser publicada entre a checagem e o commit, e
 * candidato, regra ou eleitor entraria numa votação já publicada. Com o lock, a publicação que
 * chegar depois espera e enxerga o estado já atualizado.
 *
 * <p>Quem decide o que é permitido é o estado, via {@link ComportamentosDeEstado} — aqui só
 * carrega, delega e propaga. A tabela de transições continua no {@code VotacaoStateServiceImpl}
 * (SPECS §7.3).
 */
@Service
public class ExigenciaDeEstadoDaVotacao {

    private final VotacaoRepository votacaoRepository;

    public ExigenciaDeEstadoDaVotacao(VotacaoRepository votacaoRepository) {
        this.votacaoRepository = votacaoRepository;
    }

    /**
     * Carrega a votação com lock de escrita e exige que o estado atual permita a operação.
     *
     * <p>O {@code @Transactional} não é decorativo: o lock pessimista exige transação ativa mesmo
     * quando o chamador não tem a própria — mesmo racional documentado em {@code
     * VotacaoStateServiceImpl#transitar}. Com chamador transacionado, a transação é a mesma e o
     * lock vale até o commit dele.
     *
     * @param votacaoId Votação alvo.
     * @param operacao Operação de escrita que se quer realizar.
     * @return a votação já carregada, para quem precisa dos dados depois (regras, pesos).
     * @throws RecursoNaoEncontradoException (404) se a votação não existir.
     * @throws com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException (409) se o
     *     estado não permitir a operação.
     */
    @Transactional
    public Votacao exigir(UUID votacaoId, OperacaoVotacao operacao) {
        Votacao votacao =
                votacaoRepository
                        .buscarParaTransicao(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        ComportamentosDeEstado.de(votacao.getEstado()).exigir(operacao);
        return votacao;
    }
}
