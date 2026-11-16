package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CargoPublico;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.EleicaoPublica;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.PropostaCandidato;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CandidatosDaEleicaoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.EleicaoPublicaResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.FonteResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.PerfilCandidatoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CandidatoPublicoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CargoAnteriorRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.CargoPublicoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.EleicaoPublicaRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.NoticiaCandidatoRepository;
import com.unicamp.engsoft.eleicao.eleicaopublica.repository.PropostaCandidatoRepository;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final EleicaoPublicaRepository eleicoes;
    private final CargoPublicoRepository cargos;
    private final CandidatoPublicoRepository candidatos;
    private final CargoAnteriorRepository cargosAnteriores;
    private final PropostaCandidatoRepository propostas;
    private final NoticiaCandidatoRepository noticias;

    public EleicaoPublicaService(
            EleicaoPublicaRepository eleicoes,
            CargoPublicoRepository cargos,
            CandidatoPublicoRepository candidatos,
            CargoAnteriorRepository cargosAnteriores,
            PropostaCandidatoRepository propostas,
            NoticiaCandidatoRepository noticias) {
        this.eleicoes = eleicoes;
        this.cargos = cargos;
        this.candidatos = candidatos;
        this.cargosAnteriores = cargosAnteriores;
        this.propostas = propostas;
        this.noticias = noticias;
    }

    /** RF-30. */
    public List<EleicaoPublicaResponse> listarEleicoes() {
        return eleicoes.findAllByOrderByDataPrimeiroTurnoDescNomeAsc().stream()
                .map(EleicaoPublicaResponse::de)
                .toList();
    }

    /** RF-30/RF-31: candidatos agrupados por cargo, na ordem da urna. */
    public CandidatosDaEleicaoResponse listarCandidatosPorCargo(UUID eleicaoId) {
        EleicaoPublica eleicao = buscarEleicao(eleicaoId);

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

        return new CandidatosDaEleicaoResponse(EleicaoPublicaResponse.de(eleicao), grupos);
    }

    /** RF-31/RF-33: perfil com histórico, propostas por tema e notícias. */
    public PerfilCandidatoResponse perfilDoCandidato(UUID candidatoId) {
        CandidatoPublico candidato =
                candidatos
                        .buscarComCargo(candidatoId)
                        .orElseThrow(
                                () -> new RecursoNaoEncontradoException("Candidato", candidatoId));
        CargoPublico cargo = candidato.getCargo();
        EleicaoPublica eleicao = buscarEleicao(cargo.getEleicaoId());

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
                        .toList());
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
