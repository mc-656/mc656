package com.unicamp.engsoft.eleicao.votacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import com.unicamp.engsoft.eleicao.shared.exception.RecursoNaoEncontradoException;
import com.unicamp.engsoft.eleicao.votacao.domain.EstadoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.OrigemTransicao;
import com.unicamp.engsoft.eleicao.votacao.domain.TipoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoInvalidaException;
import com.unicamp.engsoft.eleicao.votacao.domain.TransicaoVotacao;
import com.unicamp.engsoft.eleicao.votacao.domain.Votacao;
import com.unicamp.engsoft.eleicao.votacao.repository.TransicaoVotacaoRepository;
import com.unicamp.engsoft.eleicao.votacao.repository.VotacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tabela de transições, guardas e auditoria do state service, sem banco (#24, #55).
 *
 * <p>Organizado por particionamento em classes de equivalência das entradas de {@code transitar}
 * (#151): votação, par estado atual → destino, origem ({@code USUARIO} com autor, {@code SISTEMA}
 * sem), data de fim, motivo, {@code novoFimEm} e guardas de publicação. Cada teste traz no
 * {@code @DisplayName} o ID da classe:
 *
 * <ul>
 *   <li>CE-V1: par previsto na tabela e origem permitida;
 *   <li>CE-V2: transição que não exige motivo, sem motivo;
 *   <li>CE-V3: transição que exige motivo, com motivo de até 500 caracteres;
 *   <li>CE-V4: {@code Apuracao} com data de fim já atingida;
 *   <li>CE-V5: retomada de {@code Suspensa} com prorrogação válida;
 *   <li>CE-V6: publicação com todas as guardas aprovando;
 *   <li>CE-I1: votação inexistente;
 *   <li>CE-I2: par não previsto na tabela (inclui saída de estado final);
 *   <li>CE-I3: usuário dispara transição que só ocorre por data;
 *   <li>CE-I4: sistema dispara transição que exige usuário;
 *   <li>CE-I5: {@code Apuracao} antes da data de fim;
 *   <li>CE-I6: motivo exigido nulo, vazio ou em branco;
 *   <li>CE-I7: motivo acima de 500 caracteres, com ou sem o registro da prorrogação;
 *   <li>CE-I8: prorrogação fora da retomada de {@code Suspensa};
 *   <li>CE-I9: novo fim que não passa do fim atual;
 *   <li>CE-I10: novo fim que não está no futuro;
 *   <li>CE-I11: guarda de publicação reprova.
 * </ul>
 *
 * <p>Toda classe inválida confere também que o estado não mudou e que nada foi auditado.
 */
@ExtendWith(MockitoExtension.class)
class VotacaoStateServiceImplTest {

    private static final UUID VOTACAO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-06T12:00:00Z");

    @Mock VotacaoRepository votacaoRepository;

    @Mock TransicaoVotacaoRepository transicaoRepository;

    /** Guardas falsas: registram as chamadas e falham quando configuradas para isso. */
    private final List<UUID> guardasChamadas = new ArrayList<>();

    private boolean guardaFalha;

    private VotacaoStateServiceImpl service;

    @BeforeEach
    void configura() {
        GuardaPublicacao guardaQueRegistra = guardasChamadas::add;
        GuardaPublicacao guardaQuePodeFalhar =
                votacaoId -> {
                    if (guardaFalha) {
                        throw new TransicaoInvalidaException("guarda falsa reprovou");
                    }
                };
        service =
                new VotacaoStateServiceImpl(
                        votacaoRepository,
                        transicaoRepository,
                        List.of(guardaQueRegistra, guardaQuePodeFalhar),
                        Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /** Votação em memória no estado pedido, devolvida pelo repositório mockado. */
    private Votacao votacaoEm(EstadoVotacao estado) {
        return votacaoEm(estado, AGORA.plusSeconds(7200));
    }

    /** Idem, com a data de fim escolhida (para os testes que dependem dela). */
    private Votacao votacaoEm(EstadoVotacao estado, Instant fimEm) {
        Votacao votacao =
                new Votacao(
                        TipoVotacao.ELEICAO_PRIVADA,
                        fimEm.minusSeconds(3600),
                        fimEm,
                        "Eleição",
                        "Descrição");
        votacao.alterarEstado(estado);
        when(votacaoRepository.buscarParaTransicao(VOTACAO)).thenReturn(Optional.of(votacao));
        return votacao;
    }

    private TransicaoVotacao auditoriaSalva() {
        ArgumentCaptor<TransicaoVotacao> captor = ArgumentCaptor.forClass(TransicaoVotacao.class);
        verify(transicaoRepository).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "{0} -> {1} por {2}")
    @CsvSource({
        "RascunhoVotacao, Publicada, USUARIO",
        "Publicada, RascunhoVotacao, USUARIO",
        "Publicada, EmVotacao, SISTEMA",
        "Publicada, Cancelada, USUARIO",
        "EmVotacao, Suspensa, USUARIO",
        "Suspensa, EmVotacao, USUARIO",
        "EmVotacao, Cancelada, USUARIO",
        "Suspensa, Cancelada, USUARIO",
        "Apuracao, Anulada, USUARIO",
        "Apuracao, Anulada, SISTEMA"
    })
    @DisplayName("CE-V1: transições válidas alteram o estado e geram auditoria")
    void transicoesValidas(EstadoVotacao origem, EstadoVotacao destino, OrigemTransicao quem) {
        // Arrange
        Votacao votacao = votacaoEm(origem);
        UUID autor = quem == OrigemTransicao.USUARIO ? ADMIN : null;

        // Act
        service.transitar(VOTACAO, destino, autor, "motivo do teste");

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(destino);
        TransicaoVotacao auditoria = auditoriaSalva();
        assertThat(auditoria.getVotacaoId()).isEqualTo(VOTACAO);
        assertThat(auditoria.getEstadoAnterior()).isEqualTo(origem);
        assertThat(auditoria.getEstadoNovo()).isEqualTo(destino);
        assertThat(auditoria.getAutorId()).isEqualTo(autor);
        assertThat(auditoria.getOrigem()).isEqualTo(quem);
        assertThat(auditoria.getMotivo()).isEqualTo("motivo do teste");
        assertThat(auditoria.getOcorridaEm()).isEqualTo(AGORA);
    }

    @ParameterizedTest(name = "{0} -> {1} por {2}")
    @CsvSource({
        "RascunhoVotacao, EmVotacao, USUARIO",
        "RascunhoVotacao, Cancelada, USUARIO",
        "Cancelada, Publicada, USUARIO",
        "EmVotacao, Publicada, USUARIO",
        "Suspensa, Apuracao, SISTEMA",
        "Suspensa, Apuracao, USUARIO",
        "Publicada, Suspensa, USUARIO",
        "Cancelada, EmVotacao, USUARIO",
        "Cancelada, Suspensa, USUARIO",
        "Cancelada, Apuracao, SISTEMA",
        "Apuracao, EmVotacao, USUARIO",
        "Apuracao, Suspensa, USUARIO",
        "EmVotacao, Anulada, USUARIO",
        "Suspensa, Anulada, USUARIO",
        "Anulada, Homologada, USUARIO",
        "Anulada, Encerrada, USUARIO",
        "Anulada, Encerrada, SISTEMA",
        "Anulada, Apuracao, SISTEMA",
        "Anulada, Cancelada, USUARIO"
    })
    @DisplayName("CE-I2: transições não previstas são rejeitadas sem alterar estado nem auditar")
    void transicoesNaoPrevistas(EstadoVotacao origem, EstadoVotacao destino, OrigemTransicao quem) {
        assertTransicaoRejeitada(origem, destino, quem, "não prevista na máquina de estados");
    }

    @ParameterizedTest(name = "{0} -> {1} por USUARIO")
    @CsvSource({"Publicada, EmVotacao", "EmVotacao, Apuracao"})
    @DisplayName("CE-I3: usuário não dispara transição que só ocorre por data")
    void usuarioEmTransicaoAutomatica(EstadoVotacao origem, EstadoVotacao destino) {
        assertTransicaoRejeitada(
                origem, destino, OrigemTransicao.USUARIO, "apenas automaticamente, por data");
    }

    @ParameterizedTest(name = "{0} -> {1} por SISTEMA")
    @CsvSource({
        "RascunhoVotacao, Publicada",
        "Publicada, Cancelada",
        "EmVotacao, Suspensa",
        "Suspensa, EmVotacao",
        "Suspensa, Cancelada"
    })
    @DisplayName("CE-I4: sistema não dispara transição que exige ação de um usuário")
    void sistemaEmTransicaoDeUsuario(EstadoVotacao origem, EstadoVotacao destino) {
        assertTransicaoRejeitada(
                origem, destino, OrigemTransicao.SISTEMA, "exige ação de um usuário");
    }

    /**
     * Confere que a transição é recusada com o motivo esperado e deixa a votação intacta. O motivo
     * enviado é preenchido para que a recusa não venha da checagem de motivo obrigatório.
     */
    private void assertTransicaoRejeitada(
            EstadoVotacao origem, EstadoVotacao destino, OrigemTransicao quem, String mensagem) {
        // Arrange
        Votacao votacao = votacaoEm(origem);
        UUID autor = quem == OrigemTransicao.USUARIO ? ADMIN : null;

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, destino, autor, "motivo do teste"))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining(mensagem)
                .satisfies(
                        e -> {
                            TransicaoInvalidaException ex = (TransicaoInvalidaException) e;
                            assertThat(ex.getEstadoAtual()).isEqualTo(origem);
                            assertThat(ex.getEstadoDestino()).isEqualTo(destino);
                        });
        assertThat(votacao.getEstado()).isEqualTo(origem);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CE-I2: Anulada e Cancelada são finais: não têm nenhuma transição de saída")
    void estadosFinaisSemSaida() {
        // Assert
        assertThat(VotacaoStateServiceImpl.TRANSICOES)
                .doesNotContainKeys(EstadoVotacao.Anulada, EstadoVotacao.Cancelada);
    }

    @Test
    @DisplayName("CE-V6: publicação executa todas as guardas registradas")
    void publicacaoExecutaGuardas() {
        // Arrange
        votacaoEm(EstadoVotacao.RascunhoVotacao);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null);

        // Assert
        assertThat(guardasChamadas).containsExactly(VOTACAO);
    }

    @Test
    @DisplayName("CE-I11: publicação é bloqueada quando alguma guarda falha")
    void guardaFalhaBloqueiaPublicacao() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.RascunhoVotacao);
        guardaFalha = true;

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("guarda falsa reprovou");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.RascunhoVotacao);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CE-V1: guardas de publicação não rodam em outras transições")
    void guardasSoNaPublicacao() {
        // Arrange
        votacaoEm(EstadoVotacao.Publicada);
        guardaFalha = true;

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Cancelada, ADMIN, "desistimos");

        // Assert
        assertThat(guardasChamadas).isEmpty();
    }

    @Test
    @DisplayName("CE-I1: votação inexistente resulta em RecursoNaoEncontradoException")
    void votacaoInexistente() {
        // Arrange
        when(votacaoRepository.buscarParaTransicao(VOTACAO)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Publicada, ADMIN, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("CE-V4: EmVotacao → Apuracao ocorre por data de fim atingida, com origem SISTEMA")
    void apuracaoPorDataDeFim() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao, AGORA);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Apuracao, null, "Data de fim atingida");

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Apuracao);
        TransicaoVotacao auditoria = auditoriaSalva();
        assertThat(auditoria.getOrigem()).isEqualTo(OrigemTransicao.SISTEMA);
        assertThat(auditoria.getAutorId()).isNull();
        assertThat(auditoria.getEstadoAnterior()).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName("CE-I5: EmVotacao → Apuracao é rejeitada antes da data de fim")
    void apuracaoAntesDoFimRejeitada() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao, AGORA.plusSeconds(1));

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Apuracao, null, null))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("data de fim ainda não atingida");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
        verify(transicaoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} -> {1} com motivo vazio")
    @CsvSource({
        "EmVotacao, Suspensa",
        "EmVotacao, Cancelada",
        "Suspensa, Cancelada",
        "Apuracao, Anulada"
    })
    @DisplayName("CE-I6: suspensão, cancelamento forçado e anulação exigem motivo não vazio")
    void motivoObrigatorio(EstadoVotacao origem, EstadoVotacao destino) {
        for (String motivo : new String[] {null, "", "   "}) {
            // Arrange
            Votacao votacao = votacaoEm(origem);

            // Act + Assert
            assertThatThrownBy(() -> service.transitar(VOTACAO, destino, ADMIN, motivo))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessageContaining("Motivo é obrigatório");
            assertThat(votacao.getEstado()).isEqualTo(origem);
        }
        verify(transicaoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "motivo = [{0}]")
    @NullAndEmptySource
    @DisplayName("CE-V2: retomada da votação suspensa não exige motivo")
    void retomadaSemMotivo(String motivo) {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.EmVotacao, ADMIN, motivo);

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
    }

    @Test
    @DisplayName(
            "CE-V5: retomada com prorrogação estende o fim e registra a prorrogação na auditoria")
    void retomadaComProrrogacao() {
        // Arrange
        Instant fimVencido = AGORA.minusSeconds(60);
        Instant novoFim = AGORA.plusSeconds(86400);
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa, fimVencido);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.EmVotacao, ADMIN, "Problema resolvido", novoFim);

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
        assertThat(votacao.getFimEm()).isEqualTo(novoFim);
        assertThat(auditoriaSalva().getMotivo())
                .startsWith("Problema resolvido")
                .contains(fimVencido.toString())
                .contains(novoFim.toString());
    }

    @ParameterizedTest(name = "novo fim = agora + {0}s")
    @ValueSource(longs = {-30, 0})
    @DisplayName("CE-I10: prorrogação para data que não está no futuro é rejeitada")
    void prorrogacaoParaOPassadoRejeitada(long deslocamento) {
        // Arrange
        Instant fimVencido = AGORA.minusSeconds(60);
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa, fimVencido);

        // Act + Assert
        assertThatThrownBy(
                        () ->
                                service.transitar(
                                        VOTACAO,
                                        EstadoVotacao.EmVotacao,
                                        ADMIN,
                                        null,
                                        AGORA.plusSeconds(deslocamento)))
                .isInstanceOf(DadosInvalidosException.class);
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(votacao.getFimEm()).isEqualTo(fimVencido);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CE-I9: prorrogação que não passa do fim atual é rejeitada")
    void prorrogacaoQueEncurtaRejeitada() {
        // Arrange
        Instant fim = AGORA.plusSeconds(7200);
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa, fim);

        // Act + Assert
        assertThatThrownBy(
                        () ->
                                service.transitar(
                                        VOTACAO,
                                        EstadoVotacao.EmVotacao,
                                        ADMIN,
                                        null,
                                        fim.minusSeconds(1)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("posterior à data de fim atual");
        assertThat(votacao.getFimEm()).isEqualTo(fim);
    }

    @Test
    @DisplayName("CE-I8: prorrogação fora da retomada de votação suspensa é rejeitada")
    void prorrogacaoForaDaRetomadaRejeitada() {
        // Arrange
        Instant fim = AGORA.plusSeconds(7200);
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao, fim);

        // Act + Assert
        assertThatThrownBy(
                        () ->
                                service.transitar(
                                        VOTACAO,
                                        EstadoVotacao.Suspensa,
                                        ADMIN,
                                        "pane",
                                        fim.plusSeconds(3600)))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("retomar uma votação suspensa");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
        assertThat(votacao.getFimEm()).isEqualTo(fim);
    }

    @Test
    @DisplayName(
            "CE-I7: motivo que não cabe junto com o registro da prorrogação é rejeitado, sem truncar")
    void motivoLongoComProrrogacaoRejeitado() {
        // Arrange
        Instant fim = AGORA.minusSeconds(60);
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa, fim);
        String motivoMaximo = "a".repeat(VotacaoStateServiceImpl.TAMANHO_MAXIMO_MOTIVO);

        // Act + Assert
        assertThatThrownBy(
                        () ->
                                service.transitar(
                                        VOTACAO,
                                        EstadoVotacao.EmVotacao,
                                        ADMIN,
                                        motivoMaximo,
                                        AGORA.plusSeconds(86400)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("Motivo excede");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(votacao.getFimEm()).isEqualTo(fim);
        verify(transicaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CE-I7: motivo acima do tamanho da coluna é rejeitado mesmo fora do controller")
    void motivoLongoRejeitado() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao);
        String motivo = "a".repeat(VotacaoStateServiceImpl.TAMANHO_MAXIMO_MOTIVO + 1);

        // Act + Assert
        assertThatThrownBy(() -> service.transitar(VOTACAO, EstadoVotacao.Suspensa, ADMIN, motivo))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("Motivo excede");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.EmVotacao);
        verify(transicaoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} -> {1} sem motivo")
    @CsvSource({"RascunhoVotacao, Publicada", "Publicada, RascunhoVotacao", "Publicada, Cancelada"})
    @DisplayName("CE-V2: transições que não interrompem votação em andamento aceitam motivo nulo")
    void transicaoSemMotivoObrigatorio(EstadoVotacao origem, EstadoVotacao destino) {
        // Arrange
        Votacao votacao = votacaoEm(origem);

        // Act
        service.transitar(VOTACAO, destino, ADMIN, null);

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(destino);
        assertThat(auditoriaSalva().getMotivo()).isNull();
    }

    @Test
    @DisplayName("CE-V3: motivo com exatamente o tamanho da coluna é aceito e auditado inteiro")
    void motivoNoTamanhoMaximoAceito() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao);
        String motivo = "a".repeat(VotacaoStateServiceImpl.TAMANHO_MAXIMO_MOTIVO);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Suspensa, ADMIN, motivo);

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Suspensa);
        assertThat(auditoriaSalva().getMotivo()).isEqualTo(motivo);
    }

    @Test
    @DisplayName("CE-V4: EmVotacao → Apuracao também ocorre com a data de fim já vencida")
    void apuracaoComFimVencido() {
        // Arrange
        Votacao votacao = votacaoEm(EstadoVotacao.EmVotacao, AGORA.minusSeconds(3600));

        // Act
        service.transitar(VOTACAO, EstadoVotacao.Apuracao, null, null);

        // Assert
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Apuracao);
    }

    @Test
    @DisplayName("CE-V5: prorrogação sem motivo audita só o registro da prorrogação")
    void prorrogacaoSemMotivo() {
        // Arrange
        Instant fimVencido = AGORA.minusSeconds(60);
        Instant novoFim = AGORA.plusSeconds(86400);
        votacaoEm(EstadoVotacao.Suspensa, fimVencido);

        // Act
        service.transitar(VOTACAO, EstadoVotacao.EmVotacao, ADMIN, null, novoFim);

        // Assert
        assertThat(auditoriaSalva().getMotivo())
                .isEqualTo("[fim prorrogado de " + fimVencido + " para " + novoFim + "]");
    }

    @Test
    @DisplayName("CE-I9: prorrogação para a mesma data de fim atual é rejeitada")
    void prorrogacaoParaOMesmoFimRejeitada() {
        // Arrange
        Instant fim = AGORA.plusSeconds(7200);
        Votacao votacao = votacaoEm(EstadoVotacao.Suspensa, fim);

        // Act + Assert
        assertThatThrownBy(
                        () -> service.transitar(VOTACAO, EstadoVotacao.EmVotacao, ADMIN, null, fim))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("posterior à data de fim atual");
        assertThat(votacao.getEstado()).isEqualTo(EstadoVotacao.Suspensa);
        verify(transicaoRepository, never()).save(any());
    }
}
