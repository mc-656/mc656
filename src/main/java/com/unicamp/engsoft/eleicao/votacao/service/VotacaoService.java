package com.unicamp.engsoft.eleicao.votacao.service;

import com.unicamp.engsoft.eleicao.shared.exception.RegraDeNegocioException;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.votacao.domain.PapelVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.dto.CriarVotacaoRequest;
import com.unicamp.engsoft.eleicao.votacao.repository.PapelVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VotacaoService {

    private final VotacaoRepository votacaoRepository;
    private final PapelVotacaoRepository papelVotacaoRepository;
    private final MotorApuracao motorApuracao;
    private final CandidatoOuChapaRepository candidatoRepository;
    public VotacaoService(
            VotacaoRepository votacaoRepository, PapelVotacaoRepository papelVotacaoRepository, MotorApuracao motorApuracao, 
            CandidatoOuChapaRepository candidatoRepository) {
        this.votacaoRepository = votacaoRepository;
        this.papelVotacaoRepository = papelVotacaoRepository;
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

        // aí temos que adicionar os eleitores válidos para a votação
        return votacao;
    }
    @Transactional
    public ResultadoApuracao apurarVotacao(UUID votacaoId) {
        Votacao votacao = votacaoRepository.findById(votacaoId)
                .orElseThrow(() -> new RegraDeNegocioException("Votação não encontrada"));

        RegraVotacao regra = votacao.getRegra();

        List<CandidatoOuChapa> candidatos = candidatoRepository.findByVotacaoIdOrderByCriadoEm(votacaoId);
        
        List<UUID> todasOpcoesIds = candidatos.stream()
                .map(CandidatoOuChapa::getId)
                .toList();
        EstrategiaDesempate estrategiaDesempate = fabricarEstrategiaDesempate(candidatos, regra.tipoDesempate());

        
        ResultadoApuracao resultado = motorApuracao.apurar(
                regra,
                votos,
                todasOpcoesIds, 
                resultado.getTotalEleitoresAptos(),    
                resultado.getTotalParticipantes(),    // Total de participantes (votos reais)
                estrategiaDesempate
        );

        return resultado;
    }

    private EstrategiaDesempate fabricarEstrategiaDesempate(List<CandidatoOuChapa> candidatos, TipoDesempate tipo) {
        if (tipo == null || tipo == TipoDesempate.NENHUM) {
            return null; 
        }
        
        if (tipo == TipoDesempate.IDADE) {
            Map<UUID, String> nomes = candidatos.stream()
            .collect(Collectors.toMap(
                CandidatoOuChapa::getId, 
                CandidatoOuChapa::getNome
            ));
           return new DesempateAlfabetico(nomes);
        }
        
        if (tipo == TipoDesempate.ALFABETICO) {
           Map<UUID, Integer> idades = candidatos.stream()
            .collect(Collectors.toMap(
                CandidatoOuChapa::getId, 
                CandidatoOuChapa::getIdade 
            ));
        return new DesempateIdade(idades);
        }
        if (tipo == TipoDesempate.ALEATORIO) {
        List<UUID> idsCandidatos = candidatos.stream()
            .map(CandidatoOuChapa::getId)
            .toList();
        return new DesempateAleatorio(idsCandidatos);
    }
        
        throw new IllegalArgumentException("Tipo de desempate não suportado: " + tipo);
    }
}
