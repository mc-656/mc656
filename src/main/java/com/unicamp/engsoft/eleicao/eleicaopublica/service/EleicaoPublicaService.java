package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CargoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.ConteudoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.EleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.PropostaCandidato;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.SituacaoEleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TipoConteudoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.TopicoDebate;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.AparicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CandidatosDaEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse.CandidatoComparado;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse.LinhaTema;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse.PropostaItem;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ComparacaoPropostasResponse.PropostasDoCandidato;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse.CandidatoFiltroResumo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse.ConteudoPublicoItemResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse.TemaDebateResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ConteudosEleicaoResponse.TopicoDebateResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.EleicaoPublicaResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.FonteResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.PerfilCandidatoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.AparicaoCandidatoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CandidatoPublicoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CargoAnteriorRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CargoPublicoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.ConteudoPublicoCandidatoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.ConteudoPublicoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.EleicaoPublicaRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.NoticiaCandidatoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.PropostaCandidatoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.TopicoDebateRepository;
import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta do módulo informativo de eleições públicas (RF-30, RF-31, RF-33). Somente leitura: o
 * conteúdo é dado externo (TSE, imprensa) e entra por migração/importação, nunca pela API.
 */
@Service
@Transactional(readOnly = true)
public class EleicaoPublicaService {

    private static final int TOPICOS_MAXIMOS_POR_TEMA = 5;
    private static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final EleicaoPublicaRepository eleicoes;
    private final CargoPublicoRepository cargos;
    private final CandidatoPublicoRepository candidatos;
    private final CargoAnteriorRepository cargosAnteriores;
    private final PropostaCandidatoRepository propostas;
    private final NoticiaCandidatoRepository noticias;
    private final AparicaoCandidatoRepository aparicoes;
    private final ConteudoPublicoRepository conteudosPublicos;
    private final ConteudoPublicoCandidatoRepository conteudosCandidatos;
    private final TopicoDebateRepository topicosDebate;
    private final Clock clock;

    public EleicaoPublicaService(
            EleicaoPublicaRepository eleicoes,
            CargoPublicoRepository cargos,
            CandidatoPublicoRepository candidatos,
            CargoAnteriorRepository cargosAnteriores,
            PropostaCandidatoRepository propostas,
            NoticiaCandidatoRepository noticias,
            AparicaoCandidatoRepository aparicoes,
            ConteudoPublicoRepository conteudosPublicos,
            ConteudoPublicoCandidatoRepository conteudosCandidatos,
            TopicoDebateRepository topicosDebate,
            Clock clock) {
        this.eleicoes = eleicoes;
        this.cargos = cargos;
        this.candidatos = candidatos;
        this.cargosAnteriores = cargosAnteriores;
        this.propostas = propostas;
        this.noticias = noticias;
        this.aparicoes = aparicoes;
        this.conteudosPublicos = conteudosPublicos;
        this.conteudosCandidatos = conteudosCandidatos;
        this.topicosDebate = topicosDebate;
        this.clock = clock;
    }

    /** RF-30: eleições ativas e futuras por padrão (exclui encerradas). */
    public List<EleicaoPublicaResponse> listarEleicoes() {
        return listarEleicoes(null, null);
    }

    /**
     * RF-30 (#131, #88): filtra por situação (por padrão exclui {@code ENCERRADA}) e por nome de
     * cargo em disputa.
     */
    public List<EleicaoPublicaResponse> listarEleicoes(
            SituacaoEleicaoPublica situacao, String cargo) {
        LocalDate hoje = LocalDate.now(clock);
        List<EleicaoPublica> filtradas =
                eleicoes.findAllByOrderByDataPrimeiroTurnoDescNomeAsc().stream()
                        .filter(
                                e -> {
                                    SituacaoEleicaoPublica atual = e.situacaoEm(hoje);
                                    return situacao == null
                                            ? atual != SituacaoEleicaoPublica.ENCERRADA
                                            : atual == situacao;
                                })
                        .toList();

        if (cargo != null && !cargo.isBlank() && !filtradas.isEmpty()) {
            String termo = cargo.trim().toLowerCase(Locale.ROOT);
            List<UUID> idsEleicoes = filtradas.stream().map(EleicaoPublica::getId).toList();
            Set<UUID> compativeis =
                    cargos.findByEleicaoIdInOrderByOrdemAscNomeAsc(idsEleicoes).stream()
                            .filter(c -> c.getNome().toLowerCase(Locale.ROOT).contains(termo))
                            .map(CargoPublico::getEleicaoId)
                            .collect(Collectors.toSet());
            filtradas = filtradas.stream().filter(e -> compativeis.contains(e.getId())).toList();
        }

        return filtradas.stream().map(e -> EleicaoPublicaResponse.de(e, hoje)).toList();
    }

    /** RF-30/RF-31: candidatos agrupados por cargo, na ordem da urna. */
    public CandidatosDaEleicaoResponse listarCandidatosPorCargo(UUID eleicaoId) {
        EleicaoPublica eleicao = buscarEleicao(eleicaoId);
        LocalDate hoje = LocalDate.now(clock);

        Map<UUID, List<CandidatosDaEleicaoResponse.Candidato>> porCargo =
                candidatos.buscarPorEleicao(eleicaoId).stream()
                        .collect(
                                Collectors.groupingBy(
                                        c -> c.getCargo().getId(),
                                        Collectors.mapping(
                                                CandidatosDaEleicaoResponse.Candidato::de,
                                                Collectors.toList())));

        List<CandidatosDaEleicaoResponse.Cargo> grupos =
                cargos.findByEleicaoIdOrderByOrdemAscNomeAsc(eleicaoId).stream()
                        .map(
                                cargo ->
                                        new CandidatosDaEleicaoResponse.Cargo(
                                                cargo.getId(),
                                                cargo.getNome(),
                                                porCargo.getOrDefault(cargo.getId(), List.of())))
                        .toList();

        return new CandidatosDaEleicaoResponse(EleicaoPublicaResponse.de(eleicao, hoje), grupos);
    }

    /** RF-31/RF-33 (#132): perfil com histórico, propostas, notícias e agenda futura. */
    public PerfilCandidatoResponse perfilDoCandidato(UUID candidatoId) {
        CandidatoPublico candidato =
                candidatos
                        .buscarComCargo(candidatoId)
                        .orElseThrow(
                                () -> new RecursoNaoEncontradoException("Candidato", candidatoId));
        CargoPublico cargo = candidato.getCargo();
        EleicaoPublica eleicao = buscarEleicao(cargo.getEleicaoId());

        List<AparicaoResponse> agenda =
                aparicoes
                        .findByCandidatoIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                                candidatoId, clock.instant())
                        .stream()
                        .map(AparicaoResponse::de)
                        .toList();

        return new PerfilCandidatoResponse(
                candidato.getId(),
                candidato.getNomeUrna(),
                candidato.getNomeCompleto(),
                candidato.getNumero(),
                candidato.getPartidoSigla(),
                candidato.getPartidoNome(),
                new PerfilCandidatoResponse.CargoDisputado(
                        cargo.getId(),
                        cargo.getNome(),
                        cargo.idDidatico(),
                        eleicao.getId(),
                        eleicao.getNome()),
                FonteResponse.de(candidato.getFonte()),
                cargosAnteriores.findByCandidatoIdOrderByAnoInicioDesc(candidatoId).stream()
                        .map(PerfilCandidatoResponse.CargoAnteriorResponse::de)
                        .toList(),
                agruparPorTema(propostas.findByCandidatoIdOrderByTemaAscTituloAsc(candidatoId)),
                noticias.findByCandidatoIdOrderByPublicadaEmDesc(candidatoId).stream()
                        .map(PerfilCandidatoResponse.NoticiaResponse::de)
                        .toList(),
                agenda);
    }

    /**
     * RF-31 (#89): compara lado a lado propostas por tema entre 2 e 4 candidatos ao mesmo cargo na
     * eleição informada.
     */
    public ComparacaoPropostasResponse compararPropostas(UUID eleicaoId, List<UUID> candidatoIds) {
        EleicaoPublica eleicao = buscarEleicao(eleicaoId);
        List<UUID> ids =
                candidatoIds == null
                        ? List.of()
                        : candidatoIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() < 2 || ids.size() > 4) {
            throw new DadosInvalidosException(
                    "Selecione entre 2 e 4 candidatos distintos para comparar propostas");
        }

        List<CandidatoPublico> selecionados =
                ids.stream()
                        .map(
                                id ->
                                        candidatos
                                                .buscarComCargo(id)
                                                .orElseThrow(
                                                        () ->
                                                                new RecursoNaoEncontradoException(
                                                                        "Candidato", id)))
                        .toList();

        if (selecionados.stream().anyMatch(c -> !c.getCargo().getEleicaoId().equals(eleicaoId))) {
            throw new DadosInvalidosException(
                    "Todos os candidatos selecionados devem pertencer à eleição informada");
        }

        CargoPublico cargoComum = selecionados.getFirst().getCargo();
        if (selecionados.stream().anyMatch(c -> !c.getCargo().getId().equals(cargoComum.getId()))) {
            throw new DadosInvalidosException(
                    "A comparação de propostas exige candidatos concorrendo ao mesmo cargo");
        }

        List<PropostaCandidato> todasPropostas =
                propostas.findByCandidatoIdInOrderByTemaAscTituloAsc(ids);
        Set<String> temas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<UUID, Map<String, List<PropostaItem>>> porCandidatoETema = new LinkedHashMap<>();
        for (UUID id : ids) {
            porCandidatoETema.put(id, new LinkedHashMap<>());
        }
        for (PropostaCandidato p : todasPropostas) {
            temas.add(p.getTema());
            porCandidatoETema
                    .get(p.getCandidatoId())
                    .computeIfAbsent(p.getTema(), k -> new java.util.ArrayList<>())
                    .add(
                            new PropostaItem(
                                    p.getId(),
                                    p.getTitulo(),
                                    p.getDescricao(),
                                    FonteResponse.de(p.getFonte())));
        }

        List<LinhaTema> linhas =
                temas.stream()
                        .map(
                                tema ->
                                        new LinhaTema(
                                                tema,
                                                ids.stream()
                                                        .map(
                                                                cid ->
                                                                        new PropostasDoCandidato(
                                                                                cid,
                                                                                porCandidatoETema
                                                                                        .get(cid)
                                                                                        .getOrDefault(
                                                                                                tema,
                                                                                                List
                                                                                                        .of())))
                                                        .toList()))
                        .toList();

        List<CandidatoComparado> cabecalho =
                selecionados.stream()
                        .map(
                                c ->
                                        new CandidatoComparado(
                                                c.getId(),
                                                c.getNomeUrna(),
                                                c.getNumero(),
                                                c.getPartidoSigla(),
                                                c.getPartidoNome(),
                                                FonteResponse.de(c.getFonte())))
                        .toList();

        return new ComparacaoPropostasResponse(
                EleicaoPublicaResponse.de(eleicao, LocalDate.now(clock)),
                cargoComum.getId(),
                cargoComum.getNome(),
                cabecalho,
                linhas);
    }

    /** RF-33 (#133, #135): notícias e resumos estruturados de debates paginados e filtráveis. */
    public ConteudosEleicaoResponse listarConteudos(
            UUID eleicaoId,
            TipoConteudoPublico tipo,
            UUID candidatoId,
            String tema,
            int page,
            int size) {
        if (page < 0) {
            throw new DadosInvalidosException("O índice da página não pode ser negativo");
        }
        if (size < 1 || size > TAMANHO_MAXIMO_PAGINA) {
            throw new DadosInvalidosException(
                    "O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA);
        }
        EleicaoPublica eleicao = buscarEleicao(eleicaoId);
        List<ConteudoPublico> todos =
                conteudosPublicos.findByEleicaoIdOrderByPublicadaEmDescTituloAsc(eleicaoId);

        Map<UUID, List<UUID>> candidatosPorConteudo =
                todos.isEmpty()
                        ? Map.of()
                        : conteudosCandidatos
                                .findByIdConteudoIdIn(
                                        todos.stream().map(ConteudoPublico::getId).toList())
                                .stream()
                                .collect(
                                        Collectors.groupingBy(
                                                cpc -> cpc.getId().getConteudoId(),
                                                Collectors.mapping(
                                                        cpc -> cpc.getId().getCandidatoId(),
                                                        Collectors.toList())));

        List<UUID> debateIds =
                todos.stream()
                        .filter(c -> c.getTipo() == TipoConteudoPublico.RESUMO_DEBATE)
                        .map(ConteudoPublico::getId)
                        .toList();
        Map<UUID, List<TemaDebateResponse>> debatesPorConteudo =
                debateIds.isEmpty()
                        ? Map.of()
                        : topicosDebate.findByConteudoIdInOrderByTemaAscOrdemAsc(debateIds).stream()
                                .collect(
                                        Collectors.groupingBy(
                                                TopicoDebate::getConteudoId,
                                                Collectors.collectingAndThen(
                                                        Collectors.toList(),
                                                        EleicaoPublicaService
                                                                ::agruparTemasDebate)));

        Set<String> temasDisponiveis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConteudoPublico c : todos) {
            if (c.getTema() != null && !c.getTema().isBlank()) {
                temasDisponiveis.add(c.getTema());
            }
            for (TemaDebateResponse td : debatesPorConteudo.getOrDefault(c.getId(), List.of())) {
                temasDisponiveis.add(td.tema());
            }
        }

        List<CandidatoFiltroResumo> candidatosDisponiveis =
                candidatos.buscarPorEleicao(eleicaoId).stream()
                        .map(
                                c ->
                                        new CandidatoFiltroResumo(
                                                c.getId(),
                                                c.getNomeUrna(),
                                                c.getPartidoSigla(),
                                                c.getCargo().getNome()))
                        .toList();

        String temaNorm =
                tema == null || tema.isBlank() ? null : tema.trim().toLowerCase(Locale.ROOT);
        List<ConteudoPublicoItemResponse> filtrados =
                todos.stream()
                        .map(
                                c ->
                                        new ConteudoPublicoItemResponse(
                                                c.getId(),
                                                c.getTipo(),
                                                c.getTitulo(),
                                                c.getTema(),
                                                c.getResumo(),
                                                c.getPublicadaEm(),
                                                candidatosPorConteudo.getOrDefault(
                                                        c.getId(), List.of()),
                                                debatesPorConteudo.getOrDefault(
                                                        c.getId(), List.of()),
                                                FonteResponse.de(c.getFonte())))
                        .filter(item -> tipo == null || item.tipo() == tipo)
                        .filter(
                                item ->
                                        candidatoId == null
                                                || item.candidatoIds().contains(candidatoId))
                        .filter(item -> temaNorm == null || combinaTema(item, temaNorm))
                        .toList();

        long totalElementos = filtrados.size();
        int totalPaginas = (int) Math.ceil((double) totalElementos / size);
        int inicio = Math.min(page * size, filtrados.size());
        int fim = Math.min(inicio + size, filtrados.size());

        return new ConteudosEleicaoResponse(
                EleicaoPublicaResponse.de(eleicao, LocalDate.now(clock)),
                filtrados.subList(inicio, fim),
                page,
                size,
                totalElementos,
                totalPaginas,
                List.copyOf(temasDisponiveis),
                candidatosDisponiveis);
    }

    private static boolean combinaTema(ConteudoPublicoItemResponse item, String temaNorm) {
        if (item.tema() != null && item.tema().toLowerCase(Locale.ROOT).contains(temaNorm)) {
            return true;
        }
        return item.temasDebate().stream()
                .anyMatch(t -> t.tema().toLowerCase(Locale.ROOT).contains(temaNorm));
    }

    private static List<TemaDebateResponse> agruparTemasDebate(List<TopicoDebate> topicos) {
        Map<String, List<TopicoDebateResponse>> porTema =
                topicos.stream()
                        .collect(
                                Collectors.groupingBy(
                                        TopicoDebate::getTema,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                t ->
                                                        new TopicoDebateResponse(
                                                                t.getOrdem(), t.getResumo()),
                                                Collectors.toList())));
        return porTema.entrySet().stream()
                .map(
                        e ->
                                new TemaDebateResponse(
                                        e.getKey(),
                                        e.getValue().stream()
                                                .limit(TOPICOS_MAXIMOS_POR_TEMA)
                                                .toList()))
                .toList();
    }

    /** {@link LinkedHashMap} preserva a ordem alfabética de tema que vem do repositório. */
    private static List<PerfilCandidatoResponse.TemaPropostas> agruparPorTema(
            List<PropostaCandidato> lista) {
        Map<String, List<PerfilCandidatoResponse.PropostaResponse>> porTema =
                lista.stream()
                        .collect(
                                Collectors.groupingBy(
                                        PropostaCandidato::getTema,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                PerfilCandidatoResponse.PropostaResponse::de,
                                                Collectors.toList())));
        return porTema.entrySet().stream()
                .map(e -> new PerfilCandidatoResponse.TemaPropostas(e.getKey(), e.getValue()))
                .toList();
    }

    private EleicaoPublica buscarEleicao(UUID eleicaoId) {
        return eleicoes.findById(eleicaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Eleição pública", eleicaoId));
    }
}
