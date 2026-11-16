package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Proposta;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.DefinirOrcamentoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostaSalvaResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.PropostasResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.PropostaRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propostas e orçamento total do orçamento participativo (RF-20).
 *
 * <p>Propostas e orçamento só mudam com a votação em {@code RascunhoVotacao}, pela mesma razão dos
 * candidatos: publicada, os eleitores já podem ter visto as opções. A checagem fica em {@link
 * ExigenciaDeEstadoDaVotacao} ({@link OperacaoVotacao#GERENCIAR_PROPOSTAS}), que também trava a
 * votação para escrita. Esse lock é o que mantém a invariante "nenhuma proposta custa mais que o
 * orçamento total": cadastro de proposta e redução do orçamento na mesma votação são serializados,
 * então nenhum dos dois decide com base num valor que o outro está trocando.
 *
 * <p>O orçamento pode ser definido antes ou depois das propostas; a invariante é checada nos dois
 * sentidos. Quando a votação passa de {@value #LIMITE_PROPOSTAS_SEM_AVISO} propostas, o organizador
 * recebe um aviso (não um erro): muitas propostas tornam a votação confusa (D10, elicitação), mas a
 * decisão de manter fica com ele.
 */
@Service
public class PropostaService {

    static final int LIMITE_PROPOSTAS_SEM_AVISO = 10;

    private final PropostaRepository propostaRepository;
    private final VotacaoRepository votacaoRepository;
    private final ExigenciaDeEstadoDaVotacao exigenciaDeEstado;

    public PropostaService(
            PropostaRepository propostaRepository,
            VotacaoRepository votacaoRepository,
            ExigenciaDeEstadoDaVotacao exigenciaDeEstado) {
        this.propostaRepository = propostaRepository;
        this.votacaoRepository = votacaoRepository;
        this.exigenciaDeEstado = exigenciaDeEstado;
    }

    @Transactional
    public PropostaSalvaResponse cadastrar(UUID votacaoId, PropostaRequest request) {
        Votacao votacao = exigirOrcamentoParticipativoEmRascunho(votacaoId);
        String titulo = request.titulo().strip();
        if (propostaRepository.existsByVotacaoIdAndTituloIgnoreCase(votacaoId, titulo)) {
            throw tituloRepetido(titulo);
        }
        exigirCustoDentroDoOrcamento(votacao, request.custo());
        Proposta proposta =
                propostaRepository.save(
                        new Proposta(
                                votacaoId, titulo, request.descricao().strip(), request.custo()));
        return new PropostaSalvaResponse(PropostaResponse.of(proposta), avisos(votacaoId));
    }

    @Transactional
    public PropostaSalvaResponse editar(UUID votacaoId, UUID propostaId, PropostaRequest request) {
        Votacao votacao = exigirOrcamentoParticipativoEmRascunho(votacaoId);
        Proposta proposta = buscar(votacaoId, propostaId);
        String titulo = request.titulo().strip();
        if (propostaRepository.existsByVotacaoIdAndTituloIgnoreCaseAndIdNot(
                votacaoId, titulo, propostaId)) {
            throw tituloRepetido(titulo);
        }
        exigirCustoDentroDoOrcamento(votacao, request.custo());
        proposta.alterar(titulo, request.descricao().strip(), request.custo());
        return new PropostaSalvaResponse(PropostaResponse.of(proposta), avisos(votacaoId));
    }

    @Transactional
    public void remover(UUID votacaoId, UUID propostaId) {
        exigirOrcamentoParticipativoEmRascunho(votacaoId);
        propostaRepository.delete(buscar(votacaoId, propostaId));
    }

    /**
     * Define (ou redefine) o orçamento total. Reduzir abaixo do custo de uma proposta já cadastrada
     * é rejeitado em vez de remover a proposta em silêncio: o organizador decide o que ajustar.
     */
    @Transactional
    public BigDecimal definirOrcamento(UUID votacaoId, DefinirOrcamentoRequest request) {
        Votacao votacao = exigirOrcamentoParticipativoEmRascunho(votacaoId);
        BigDecimal novoOrcamento = request.orcamentoTotal();
        BigDecimal maiorCusto = propostaRepository.maiorCusto(votacaoId);
        if (maiorCusto != null && maiorCusto.compareTo(novoOrcamento) > 0) {
            throw new RegraDeNegocioException(
                    ("O orçamento total (%s) não pode ser menor que o custo de uma proposta já"
                                    + " cadastrada (%s); ajuste ou remova a proposta antes")
                            .formatted(novoOrcamento.toPlainString(), maiorCusto.toPlainString()));
        }
        votacao.definirOrcamentoTotal(novoOrcamento);
        return votacao.getOrcamentoTotal();
    }

    @Transactional(readOnly = true)
    public PropostasResponse listar(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .findById(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        List<PropostaResponse> propostas =
                propostaRepository.findByVotacaoIdOrderByCriadoEm(votacaoId).stream()
                        .map(PropostaResponse::of)
                        .toList();
        return new PropostasResponse(
                votacao.getOrcamentoTotal(), propostas, avisos(propostas.size()));
    }

    /**
     * Propostas só existem em orçamento participativo: numa eleição privada as opções são
     * candidatos/chapas (RF-12). A checagem de tipo vem depois da de estado para que a votação já
     * esteja travada.
     */
    private Votacao exigirOrcamentoParticipativoEmRascunho(UUID votacaoId) {
        Votacao votacao = exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.GERENCIAR_PROPOSTAS);
        if (votacao.getTipo() != TipoVotacao.ORCAMENTO_PARTICIPATIVO) {
            throw new RegraDeNegocioException(
                    "Propostas e orçamento total só existem em votações de orçamento participativo");
        }
        return votacao;
    }

    private static void exigirCustoDentroDoOrcamento(Votacao votacao, BigDecimal custo) {
        BigDecimal orcamento = votacao.getOrcamentoTotal();
        if (orcamento != null && custo.compareTo(orcamento) > 0) {
            throw new RegraDeNegocioException(
                    "O custo da proposta (%s) é maior que o orçamento total da votação (%s)"
                            .formatted(custo.toPlainString(), orcamento.toPlainString()));
        }
    }

    private Proposta buscar(UUID votacaoId, UUID propostaId) {
        return propostaRepository
                .findByIdAndVotacaoId(propostaId, votacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Proposta", propostaId));
    }

    private static RegraDeNegocioException tituloRepetido(String titulo) {
        return new RegraDeNegocioException(
                "Já existe proposta com o título '%s' nesta votação".formatted(titulo));
    }

    private List<String> avisos(UUID votacaoId) {
        return avisos(propostaRepository.countByVotacaoId(votacaoId));
    }

    static List<String> avisos(long quantidadePropostas) {
        if (quantidadePropostas > LIMITE_PROPOSTAS_SEM_AVISO) {
            return List.of(
                    ("A votação tem %d propostas; mais de %d propostas podem tornar a votação"
                                    + " confusa para os eleitores")
                            .formatted(quantidadePropostas, LIMITE_PROPOSTAS_SEM_AVISO));
        }
        return List.of();
    }
}
