package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacaoId;
import com.unicamp.engsoft.eleicao.votacao.domain.RegraVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.AdicionarEleitorRequest;
import com.unicamp.engsoft.eleicao.votacao.dto.EleitorResponse;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eleitores elegíveis de uma votação e seus pesos (RF-02, RF-10).
 *
 * <p>Ser eleitor é ter o papel {@code ELEITOR} em {@code papeis_votacao}; é essa linha que a
 * checagem de elegibilidade (#23) e o quórum (#59) consultam. A lista só muda com a votação em
 * {@code RascunhoVotacao}, como os candidatos (#22): depois de publicada, o colégio eleitoral é o
 * que os participantes viram, e mexer nele mudaria o denominador do quórum. A autorização (só
 * {@code ADMIN_VOTACAO}) fica no controller, via {@code @PreAuthorize}.
 */
@Service
public class EleitorService {

    private final PapelVotacaoRepository papelVotacaoRepository;
    private final VotacaoRepository votacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConsultaRegraVotacao consultaRegraVotacao;

    public EleitorService(
            PapelVotacaoRepository papelVotacaoRepository,
            VotacaoRepository votacaoRepository,
            UsuarioRepository usuarioRepository,
            ConsultaRegraVotacao consultaRegraVotacao) {
        this.papelVotacaoRepository = papelVotacaoRepository;
        this.votacaoRepository = votacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.consultaRegraVotacao = consultaRegraVotacao;
    }

    @Transactional
    public EleitorResponse adicionar(UUID votacaoId, AdicionarEleitorRequest request) {
        Votacao votacao = exigirRascunho(votacaoId);
        BigDecimal peso = request.peso() == null ? BigDecimal.ONE : request.peso();
        validarPeso(votacao, peso);

        String email = request.email().strip();
        Usuario usuario =
                usuarioRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new RecursoNaoEncontradoException(
                                                "Nenhum usuário cadastrado com o e-mail '%s'"
                                                        .formatted(email)));
        PapelVotacaoId id = new PapelVotacaoId(usuario.getId(), votacaoId, PapelUsuario.ELEITOR);
        if (papelVotacaoRepository.existsById(id)) {
            throw new RegraDeNegocioException(
                    "O usuário '%s' já é eleitor desta votação".formatted(email));
        }
        PapelVotacao papel =
                papelVotacaoRepository.save(
                        new PapelVotacao(usuario.getId(), votacaoId, PapelUsuario.ELEITOR, peso));
        return EleitorResponse.of(usuario, papel);
    }

    /** Eleitores em ordem alfabética de nome, que é como o administrador procura alguém. */
    @Transactional(readOnly = true)
    public List<EleitorResponse> listar(UUID votacaoId) {
        if (!votacaoRepository.existsById(votacaoId)) {
            throw new RecursoNaoEncontradoException("Votação", votacaoId);
        }
        List<PapelVotacao> papeis =
                papelVotacaoRepository.findByIdVotacaoIdAndIdPapel(votacaoId, PapelUsuario.ELEITOR);
        Map<UUID, Usuario> usuarios =
                usuarioRepository
                        .findAllById(papeis.stream().map(p -> p.getId().getUsuarioId()).toList())
                        .stream()
                        .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return papeis.stream()
                .map(p -> EleitorResponse.of(usuarios.get(p.getId().getUsuarioId()), p))
                .sorted(Comparator.comparing(EleitorResponse::nome))
                .toList();
    }

    @Transactional
    public void remover(UUID votacaoId, UUID usuarioId) {
        exigirRascunho(votacaoId);
        PapelVotacaoId id = new PapelVotacaoId(usuarioId, votacaoId, PapelUsuario.ELEITOR);
        PapelVotacao papel =
                papelVotacaoRepository
                        .findById(id)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Eleitor", usuarioId));
        papelVotacaoRepository.delete(papel);
    }

    /**
     * Peso negativo não tem significado em nenhuma regra. Peso diferente de 1 só faz sentido com
     * voto ponderado: sem ele a apuração ignora o peso, e aceitar o valor daria ao administrador a
     * falsa impressão de que ele conta. Compara com {@code compareTo} para aceitar {@code 1.0}.
     */
    private void validarPeso(Votacao votacao, BigDecimal peso) {
        if (peso.signum() < 0) {
            throw new DadosInvalidosException("O peso do eleitor não pode ser negativo");
        }
        boolean votoPonderado =
                consultaRegraVotacao
                        .regraDe(votacao)
                        .map(RegraVotacao::votoPonderado)
                        .orElse(false);
        if (peso.compareTo(BigDecimal.ONE) != 0 && !votoPonderado) {
            throw new RegraDeNegocioException(
                    "Peso diferente de 1 só é permitido em votação com voto ponderado");
        }
    }

    /**
     * Usa o mesmo lock de escrita das transições ({@code buscarParaTransicao}) para que checar o
     * rascunho e alterar os eleitores seja atômico frente a uma publicação concorrente: sem ele, a
     * votação poderia ser publicada entre a checagem e o commit, e o eleitor entraria (ou sairia)
     * de uma votação já publicada. Com o lock, a operação que chegar depois espera a outra e vê o
     * estado já atualizado.
     */
    private Votacao exigirRascunho(UUID votacaoId) {
        Votacao votacao =
                votacaoRepository
                        .buscarParaTransicao(votacaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Votação", votacaoId));
        if (votacao.getEstado() != EstadoVotacao.RascunhoVotacao) {
            throw new RegraDeNegocioException(
                    "Eleitores só podem ser alterados com a votação em rascunho (estado atual: %s)"
                            .formatted(votacao.getEstado()));
        }
        return votacao;
    }
}
