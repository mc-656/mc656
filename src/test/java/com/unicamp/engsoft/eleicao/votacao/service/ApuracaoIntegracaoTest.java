package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unicamp.engsoft.eleicao.AbstractIntegrationTest;
import com.unicamp.engsoft.eleicao.shared.security.UsuarioAutenticado;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import com.unicamp.engsoft.eleicao.usuario.repository.UsuarioRepository;
import com.unicamp.engsoft.eleicao.usuario.service.TokenService;
import com.unicamp.engsoft.eleicao.votacao.domain.*;
import com.unicamp.engsoft.eleicao.votacao.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ApuracaoIntegracaoTest extends AbstractIntegrationTest {
    @Autowired ApuracaoService apuracao;
    @Autowired VotacaoStateService estados;
    @Autowired RegistroVotoService registro;
    @Autowired VotacaoService votacaoService;
    @Autowired VotacaoRepository votacoes;
    @Autowired ApuracaoRepository snapshots;
    @Autowired CandidatoOuChapaRepository candidatos;
    @Autowired PropostaRepository propostas;
    @Autowired PapelVotacaoRepository papeis;
    @Autowired UsuarioRepository usuarios;
    @Autowired CedulaRepository cedulas;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;

    private int proximoUsuario;

    private Usuario usuario() {
        String unico = UUID.randomUUID().toString();
        return usuarios.save(
                new Usuario(
                        "Participante",
                        List.of("12345678909", "98765432100", "11144477735", "52998224725")
                                .get(proximoUsuario++),
                        unico + "@example.com",
                        "hash"));
    }

    private Votacao votacao(RegraVotacao regra) {
        Votacao v =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        Instant.now().minusSeconds(3600),
                        Instant.now().plusSeconds(3600),
                        "Eleição",
                        "Descrição");
        v.configurarRegras(regra);
        v.alterarEstado(EstadoVotacao.EmVotacao);
        return votacoes.saveAndFlush(v);
    }

    private CandidatoOuChapa opcao(Votacao v, String nome) {
        return candidatos.save(
                new CandidatoOuChapa(
                        v.getId(), nome, "Proposta de " + nome, LocalDate.of(1980, 1, 1)));
    }

    private Usuario votar(Votacao v, CandidatoOuChapa opcao, String peso) {
        Usuario u = usuario();
        papeis.saveAndFlush(
                new PapelVotacao(u.getId(), v.getId(), PapelUsuario.ELEITOR, new BigDecimal(peso)));
        var auth =
                UsernamePasswordAuthenticationToken.authenticated(
                        UsuarioAutenticado.de(u), null, List.of());
        registro.registrar(v.getId(), opcao.getId(), auth);
        return u;
    }

    private Apuracao encerrar(Votacao v) {
        em.flush();
        jdbc.update(
                "update votacoes set fim_em = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                v.getId());
        em.clear();
        estados.transitar(v.getId(), EstadoVotacao.Apuracao, null, "Data de fim atingida");
        em.flush();
        em.clear();
        return snapshots.findById(v.getId()).orElseThrow();
    }

    @ParameterizedTest
    @EnumSource(TipoMaioria.class)
    void votosReaisGeramSnapshotPersistido(TipoMaioria maioria) {
        var regra =
                new RegraVotacao(
                        maioria,
                        maioria == TipoMaioria.MAIORIA_QUALIFICADA ? new BigDecimal("60") : null,
                        false,
                        null,
                        true);
        Votacao v = votacao(regra);
        var a = opcao(v, "A");
        var b = opcao(v, "B");
        var c = opcao(v, "C");
        votar(v, a, "2.5");
        votar(v, b, "1");
        Apuracao s = encerrar(v);
        assertThat(s.getResultado().status()).isEqualTo(StatusApuracao.DEFINIDO);
        assertThat(s.getResultado().vencedorId()).isEqualTo(a.getId());
        assertThat(s.getResultado().totalVotosValidos()).isEqualByComparingTo("3.5");
        assertThat(s.getResultado().ranking()).hasSize(3);
        assertThat(s.getResultado().ranking().getLast().opcaoId()).isEqualTo(c.getId());
        assertThat(s.getResultado().ranking().getLast().totalVotos()).isEqualByComparingTo("0");
        assertThat(s.getResultado().totalParticipantes()).isEqualTo(2);
        assertThat(s.getResultado().totalEleitoresAptos()).isEqualTo(2);
        assertThat(s.getTotalCedulas()).isEqualTo(2);
        assertThat(s.getRegra()).isEqualTo(regra);
        assertThat(s.getConcluidaEm()).isNotNull();
        assertThat(s.getSegundoTurnoId()).isNull();
        assertThat(apuracao.apurar(v.getId()).getConcluidaEm()).isEqualTo(s.getConcluidaEm());
        assertThat(votacoes.findById(v.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Apuracao);
    }

    @Test
    void quorumInsuficientePersisteResultadoEAnulaComAuditoria() {
        Votacao v =
                votacao(
                        new RegraVotacao(
                                TipoMaioria.MAIORIA_SIMPLES,
                                null,
                                false,
                                new BigDecimal("75"),
                                false));
        var a = opcao(v, "A");
        opcao(v, "B");
        votar(v, a, "1");
        Usuario ausente = usuario();
        papeis.save(new PapelVotacao(ausente.getId(), v.getId(), PapelUsuario.ELEITOR));
        Apuracao s = encerrar(v);
        assertThat(s.getResultado().status()).isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
        assertThat(s.getResultado().percentualComparecimento()).isEqualByComparingTo("50");
        assertThat(votacoes.findById(v.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Anulada);
        assertThat(estados.historico(v.getId())).hasSize(2);
        assertThat(estados.historico(v.getId()))
                .anySatisfy(
                        t -> {
                            assertThat(t.getEstadoAnterior()).isEqualTo(EstadoVotacao.Apuracao);
                            assertThat(t.getEstadoNovo()).isEqualTo(EstadoVotacao.Anulada);
                            assertThat(t.getOrigem()).isEqualTo(OrigemTransicao.SISTEMA);
                            assertThat(t.getAutorId()).isNull();
                            assertThat(t.getMotivo()).isEqualTo("quórum não atingido");
                        });
        assertThat(apuracao.apurar(v.getId()).getResultado().status())
                .isEqualTo(StatusApuracao.QUORUM_NAO_ATINGIDO);
    }

    @Test
    void segundoTurnoCopiaFinalistasPapeisEPesosUmaUnicaVez() throws Exception {
        Votacao v = votacao(new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, true));
        var a = opcao(v, "A");
        var b = opcao(v, "B");
        var c = opcao(v, "C");
        Usuario admin = usuario();
        papeis.save(new PapelVotacao(admin.getId(), v.getId(), PapelUsuario.ADMIN_VOTACAO));
        Usuario eleitor = votar(v, a, "4");
        votar(v, b, "3");
        votar(v, c, "3");
        // Resolve a disputa da segunda vaga pela regra configurada.
        v.configurarRegras(
                new RegraVotacao(
                        TipoMaioria.MAIORIA_ABSOLUTA,
                        null,
                        true,
                        null,
                        true,
                        TipoDesempate.ORDEM_ALFABETICA));
        Apuracao s = encerrar(v);
        UUID derivadaId = s.getSegundoTurnoId();
        Votacao derivada = votacoes.findById(derivadaId).orElseThrow();
        assertThat(s.getResultado().finalistasSegundoTurno()).containsExactly(a.getId(), b.getId());
        assertThat(derivada.getVotacaoOrigemId()).isEqualTo(v.getId());
        assertThat(derivada.getEstado()).isEqualTo(EstadoVotacao.RascunhoVotacao);
        assertThat(derivada.getInicioEm()).isNull();
        assertThat(derivada.getFimEm()).isNull();
        assertThat(derivada.getRegras().segundoTurno()).isFalse();
        assertThat(derivada.getRegras().tipoDesempate()).isEqualTo(TipoDesempate.ORDEM_ALFABETICA);
        assertThat(candidatos.findByVotacaoIdOrderByCriadoEm(derivadaId))
                .extracting(CandidatoOuChapa::getNome)
                .containsExactly("A", "B");
        assertThat(candidatos.findByVotacaoIdOrderByCriadoEm(derivadaId))
                .allSatisfy(
                        o -> {
                            assertThat(o.getId()).isNotIn(a.getId(), b.getId());
                            assertThat(o.getDataNascimento()).isEqualTo(LocalDate.of(1980, 1, 1));
                        });
        assertThat(papeis.findByIdVotacaoId(derivadaId)).hasSize(4);
        assertThat(
                        papeis.findById(
                                        new PapelVotacaoId(
                                                eleitor.getId(), derivadaId, PapelUsuario.ELEITOR))
                                .orElseThrow()
                                .getPeso())
                .isEqualByComparingTo("4");
        assertThat(
                        papeis.existsByIdUsuarioIdAndIdVotacaoIdAndIdPapel(
                                admin.getId(), derivadaId, PapelUsuario.ADMIN_VOTACAO))
                .isTrue();
        assertThat(cedulas.findByVotacaoId(derivadaId)).isEmpty();
        assertThat(apuracao.apurar(v.getId()).getSegundoTurnoId()).isEqualTo(derivadaId);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from votacoes where votacao_origem_id = ?",
                                Long.class,
                                v.getId()))
                .isEqualTo(1);
        assertThat(votacaoService.listarAdministradasPor(admin.getId()))
                .extracting(Votacao::getId)
                .contains(derivadaId);
        assertThat(votacaoService.listarOndeEhEleitor(eleitor.getId()))
                .extracting(Votacao::getId)
                .contains(derivadaId);

        String periodo =
                "{\"inicioEm\": \"2099-01-01T00:00:00Z\", \"fimEm\": \"2099-01-02T00:00:00Z\"}";
        mvc.perform(
                        put("/api/votacoes/{id}/periodo", derivadaId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.gerar(UsuarioAutenticado.de(eleitor)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(periodo))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/votacoes/{id}/periodo", derivadaId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.gerar(UsuarioAutenticado.de(admin)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(periodo))
                .andExpect(status().isOk());
        estados.transitar(derivadaId, EstadoVotacao.Publicada, admin.getId(), null);
        assertThat(votacoes.findById(derivadaId).orElseThrow().getEstado())
                .isEqualTo(EstadoVotacao.Publicada);
    }

    @Test
    void semVotosNaoCriaSegundoTurno() {
        Votacao v =
                votacao(new RegraVotacao(TipoMaioria.MAIORIA_ABSOLUTA, null, true, null, false));
        opcao(v, "A");
        opcao(v, "B");
        var s = encerrar(v);
        assertThat(s.getResultado().status()).isEqualTo(StatusApuracao.EMPATE);
        assertThat(s.getTotalCedulas()).isZero();
        assertThat(s.getSegundoTurnoId()).isNull();
    }

    @Test
    void orcamentoNaoUsaMotorDeEleicaoPrivada() {
        Votacao v =
                new Votacao(
                        TipoVotacao.ORCAMENTO_PARTICIPATIVO,
                        Instant.now().minusSeconds(3600),
                        Instant.now().minusSeconds(1),
                        "Orçamento",
                        "Cotas");
        v.definirOrcamentoTotal(new BigDecimal("100.00"));
        v.alterarEstado(EstadoVotacao.EmVotacao);
        v = votacoes.saveAndFlush(v);
        propostas.saveAndFlush(
                new Proposta(v.getId(), "Ciclovia", "Ciclovia central", new BigDecimal("40.00")));
        estados.transitar(v.getId(), EstadoVotacao.Apuracao, null, "Fim");
        var snapshot = snapshots.findById(v.getId()).orElseThrow();
        assertThat(snapshot.getResultado()).isNull();
        assertThat(snapshot.getResultadoOrcamento()).isNotNull();
    }
}
