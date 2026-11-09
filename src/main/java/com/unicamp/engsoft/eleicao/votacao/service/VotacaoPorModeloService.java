package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.votacao.domain.CandidatoOuChapa;
import com.unicamp.engsoft.eleicao.votacao.domain.ModeloVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CadastrarCandidatoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CandidatoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoPorModeloRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.RegraVotacaoResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoPorModeloResponse;
import com.unicamp.engsoft.eleicao.votacao.dto.VotacaoResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação de votação a partir de um modelo pronto (RF-10, RF-16).
 *
 * <p>Não tem regra própria de persistência: orquestra, numa única transação, os mesmos passos que o
 * organizador faria à mão — criar a votação ({@link VotacaoService#criarVotacao}, que já torna o
 * criador {@code ADMIN_VOTACAO}), configurar as regras ({@link VotacaoService#configurarRegras}) e
 * cadastrar as opções ({@link CandidatoOuChapaService#cadastrar}). Assim as validações de cada
 * passo (datas, estado com lock, nome duplicado) valem igualmente aqui, e qualquer falha desfaz a
 * votação inteira, sem deixar rascunho pela metade.
 *
 * <p>Fica separado do {@code VotacaoService} para não criar dependência dele com o {@code
 * CandidatoOuChapaService}: os dois são serviços irmãos, e este é o único que precisa de ambos.
 */
@Service
public class VotacaoPorModeloService {

    /**
     * Mesmo mínimo de {@link GuardaMinimoCandidatos}: exigido já na criação para que a votação saia
     * publicável sem nenhum ajuste, como pede o critério de aceite da #77.
     */
    static final int MINIMO_OPCOES = GuardaMinimoCandidatos.MINIMO_CANDIDATOS;

    private final VotacaoService votacaoService;
    private final CandidatoOuChapaService candidatoService;

    public VotacaoPorModeloService(
            VotacaoService votacaoService, CandidatoOuChapaService candidatoService) {
        this.votacaoService = votacaoService;
        this.candidatoService = candidatoService;
    }

    @Transactional
    public VotacaoPorModeloResponse criar(CriarVotacaoPorModeloRequest request, UUID criadorId) {
        ModeloVotacao modelo = request.modelo();
        List<String> opcoes = resolverOpcoes(modelo, request.opcoes());

        Votacao votacao =
                votacaoService.criarVotacao(
                        new CriarVotacaoRequest(
                                TipoVotacao.ELEICAO_PRIVADA,
                                request.inicioEm(),
                                request.fimEm(),
                                request.nome().strip(),
                                descricaoOuPadrao(request.descricao(), modelo)),
                        criadorId);
        RegraVotacao regra = votacaoService.configurarRegras(votacao.getId(), modelo.regra());

        List<CandidatoResponse> cadastradas = new ArrayList<>();
        for (String opcao : opcoes) {
            CandidatoOuChapa candidato =
                    candidatoService.cadastrar(
                            votacao.getId(), new CadastrarCandidatoRequest(opcao, null));
            cadastradas.add(CandidatoResponse.of(candidato));
        }

        return new VotacaoPorModeloResponse(
                VotacaoResponse.of(votacao),
                modelo,
                RegraVotacaoResponse.of(regra),
                List.copyOf(cadastradas));
    }

    /**
     * Valida as opções antes de qualquer escrita: duplicadas e mínimo são erros do payload (400),
     * não conflito com o estado da votação — o 409 de nome duplicado do {@link
     * CandidatoOuChapaService} não se aplicaria, já que a votação ainda nem existe para o cliente.
     */
    private static List<String> resolverOpcoes(ModeloVotacao modelo, List<String> informadas) {
        List<String> opcoes =
                (informadas == null || informadas.isEmpty())
                        ? modelo.opcoesPadrao()
                        : informadas.stream().map(String::strip).toList();
        if (opcoes.size() < MINIMO_OPCOES) {
            throw new DadosInvalidosException(
                    "O modelo %s exige no mínimo %d opções".formatted(modelo, MINIMO_OPCOES));
        }
        Set<String> vistas = new HashSet<>();
        for (String opcao : opcoes) {
            if (!vistas.add(opcao.toLowerCase(Locale.ROOT))) {
                throw new DadosInvalidosException("Opção repetida: '%s'".formatted(opcao));
            }
        }
        return opcoes;
    }

    private static String descricaoOuPadrao(String descricao, ModeloVotacao modelo) {
        return (descricao == null || descricao.isBlank()) ? modelo.descricao() : descricao.strip();
    }
}
