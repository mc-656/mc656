package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.OperacaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.ConfigurarRegrasRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VotacaoService {

    private final VotacaoRepository votacaoRepository;
    private final PapelVotacaoRepository papelVotacaoRepository;
    private final ExigenciaDeEstadoDaVotacao exigenciaDeEstado;

    public VotacaoService(
            VotacaoRepository votacaoRepository,
            PapelVotacaoRepository papelVotacaoRepository,
            ExigenciaDeEstadoDaVotacao exigenciaDeEstado) {
        this.votacaoRepository = votacaoRepository;
        this.papelVotacaoRepository = papelVotacaoRepository;
        this.exigenciaDeEstado = exigenciaDeEstado;
    }

    @Transactional
    public Votacao criarVotacao(CriarVotacaoRequest request, UUID administradorId) {
        if (request.fimEm().isBefore(request.inicioEm())) {
            throw new RegraDeNegocioException(
                    "A data de término deve ser posterior a data de início");
        }
        Votacao votacao = votacaoRepository.save(request.paraVotacao());
        papelVotacaoRepository.save(
                new PapelVotacao(administradorId, votacao.getId(), PapelUsuario.ADMIN_VOTACAO));

        return votacao;
    }

    /**
     * Configura as regras de apuração da votação (RF-11). Só enquanto ela está em {@code
     * RascunhoVotacao}: depois de publicada, eleitores já podem tê-la consultado e mudar a regra de
     * apuração mudaria o resultado esperado. A autorização (só {@code ADMIN_VOTACAO}) fica no
     * controller, via {@code @PreAuthorize}.
     *
     * <p>A checagem de estado não está aqui: fica em {@link ExigenciaDeEstadoDaVotacao}, o ponto
     * único que carrega a votação com lock (sem a corrida com uma publicação concorrente) e delega
     * a regra ao comportamento do estado. Só o rótulo da operação é deste service.
     *
     * <p>A combinação de parâmetros é validada pelo value object antes de qualquer consulta, para
     * que um payload inválido devolva 400 mesmo quando a votação nem existe.
     */
    @Transactional
    public RegraVotacao configurarRegras(UUID votacaoId, ConfigurarRegrasRequest request) {
        RegraVotacao regra = request.paraRegra();
        Votacao votacao = exigenciaDeEstado.exigir(votacaoId, OperacaoVotacao.CONFIGURAR_REGRAS);
        votacao.configurarRegras(regra);
        return regra;
    }

    /** Votação pelo id, para exibição (views do M1, #67). */
    @Transactional(readOnly = true)
    public Votacao buscar(UUID votacaoId) {
        return votacaoRepository
                .findById(votacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
    }

    /**
     * Votações em que o usuário é {@code ADMIN_VOTACAO}, as mais recentes por início primeiro. É a
     * lista de trabalho do administrador nas views (#67); votações em que ele é só eleitor ficam de
     * fora porque nelas ele não pode configurar nada.
     */
    @Transactional(readOnly = true)
    public List<Votacao> listarAdministradasPor(UUID usuarioId) {
        List<UUID> ids =
                papelVotacaoRepository
                        .findByIdUsuarioIdAndIdPapel(usuarioId, PapelUsuario.ADMIN_VOTACAO)
                        .stream()
                        .map(p -> p.getId().getVotacaoId())
                        .toList();
        return votacaoRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Votacao::getInicioEm).reversed())
                .toList();
    }
}
