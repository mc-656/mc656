package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

import com.unicamp.engsoft.eleicao.shared.exception.DadosInvalidosException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Distribuição de cadeiras do sistema proporcional brasileiro (RF-32), seguindo o Código Eleitoral
 * (Lei 4.737/1965) com a redação da Lei 14.211/2021.
 *
 * <ol>
 *   <li>Quociente eleitoral (art. 106): votos válidos / vagas, desprezada a fração igual ou menor
 *       que meio e arredondada para cima a fração maior que meio.
 *   <li>Quociente partidário (art. 107): votos do partido / QE, desprezada a fração.
 *   <li>Vagas do quociente (art. 108): vão aos mais votados do partido que tenham ao menos 10% do
 *       QE; vaga sem candidato que cumpra o mínimo vira sobra.
 *   <li>Sobras (art. 109): maiores médias, votos / (cadeiras obtidas + 1). Só disputam partidos com
 *       ao menos 80% do QE e candidatos com ao menos 20% dele (cláusula de desempenho).
 *   <li>Quando ninguém mais cumpre a cláusula, as sobras restantes vão por maiores médias entre
 *       todos os partidos (art. 109, III, como decidido pelo STF nas ADIs 7228, 7263 e 7325).
 *   <li>Se nenhum partido atinge o QE, elegem-se os mais votados (art. 111).
 * </ol>
 *
 * <p>Classe pura, sem estado nem banco: o módulo de eleições públicas é informativo e nunca toca as
 * tabelas de {@code votacao}. Todas as comparações usam inteiros (inclusive as médias, comparadas
 * por produto cruzado) para que nenhum arredondamento de ponto flutuante decida uma cadeira.
 *
 * <p>Empate de médias: leva o partido com mais votos e, persistindo, a sigla em ordem alfabética.
 * Empate de candidatos: o mais votado e, persistindo, o nome em ordem alfabética. A lei manda
 * desempatar candidatos pela idade; o exemplo didático não depende disso, então a ordem alfabética
 * apenas torna o resultado determinístico.
 */
public final class CalculadoraQuocienteEleitoral {

    private static final Comparator<CandidatoProporcional> MAIS_VOTADO_PRIMEIRO =
            CandidatoProporcional.MAIS_VOTADO_PRIMEIRO;

    private CalculadoraQuocienteEleitoral() {}

    /**
     * Quociente eleitoral com o arredondamento do art. 106: fração acima de meio conta como um,
     * fração igual ou abaixo de meio é desprezada.
     */
    public static long quocienteEleitoral(long votosValidos, int vagas) {
        if (vagas <= 0) {
            throw new DadosInvalidosException("O número de vagas deve ser positivo.");
        }
        if (votosValidos < 0) {
            throw new DadosInvalidosException("Os votos válidos não podem ser negativos.");
        }
        long quociente = votosValidos / vagas;
        long resto = votosValidos % vagas;
        return 2 * resto > vagas ? quociente + 1 : quociente;
    }

    /** Quociente partidário (art. 107): divisão inteira, desprezada a fração. */
    public static long quocientePartidario(long votosPartido, long quocienteEleitoral) {
        if (quocienteEleitoral <= 0) {
            throw new DadosInvalidosException("O quociente eleitoral deve ser positivo.");
        }
        return votosPartido / quocienteEleitoral;
    }

    public static DistribuicaoCadeiras distribuir(int vagas, List<PartidoProporcional> partidos) {
        validar(vagas, partidos);
        long votosValidos = partidos.stream().mapToLong(PartidoProporcional::votosTotais).sum();
        long qe = quocienteEleitoral(votosValidos, vagas);
        if (qe == 0) {
            throw new DadosInvalidosException(
                    "Há votos válidos de menos para o número de vagas: o quociente eleitoral seria"
                            + " zero.");
        }

        List<Apuracao> apuracoes = partidos.stream().map(p -> new Apuracao(p, qe)).toList();
        List<CandidatoEleito> eleitos = new ArrayList<>();
        boolean nenhumAtingiu = apuracoes.stream().noneMatch(a -> a.votos() >= qe);

        if (nenhumAtingiu) {
            elegerMaisVotados(vagas, apuracoes, eleitos);
            return montar(votosValidos, vagas, qe, true, apuracoes, List.of(), eleitos);
        }

        for (Apuracao a : apuracoes) {
            while (a.cadeirasPorQuociente < a.quocientePartidario && a.temCandidatoCom(10)) {
                a.cadeirasPorQuociente++;
                eleitos.add(a.eleger(CriterioEleicao.QUOCIENTE_PARTIDARIO));
            }
        }

        List<RodadaSobra> rodadas = new ArrayList<>();
        while (eleitos.size() < vagas) {
            List<Apuracao> concorrentes =
                    apuracoes.stream()
                            .filter(Apuracao::atingiuClausulaDesempenho)
                            .filter(a -> a.temCandidatoCom(20))
                            .toList();
            boolean clausula = !concorrentes.isEmpty();
            if (!clausula) {
                concorrentes = apuracoes.stream().filter(a -> a.temCandidatoCom(0)).toList();
            }
            if (concorrentes.isEmpty()) {
                break;
            }
            Apuracao vencedor = concorrentes.stream().max(Apuracao::compararMedia).orElseThrow();
            List<MediaPartido> medias = concorrentes.stream().map(Apuracao::media).toList();
            vencedor.cadeirasPorSobras++;
            CandidatoEleito eleito =
                    vencedor.eleger(
                            clausula
                                    ? CriterioEleicao.SOBRA_MAIORES_MEDIAS
                                    : CriterioEleicao.SOBRA_SEM_CLAUSULA);
            eleitos.add(eleito);
            rodadas.add(new RodadaSobra(rodadas.size() + 1, clausula, medias, eleito));
        }
        return montar(votosValidos, vagas, qe, false, apuracoes, rodadas, eleitos);
    }

    private static void elegerMaisVotados(
            int vagas, List<Apuracao> apuracoes, List<CandidatoEleito> eleitos) {
        while (eleitos.size() < vagas) {
            Apuracao melhor =
                    apuracoes.stream()
                            .filter(a -> !a.fila.isEmpty())
                            .min(
                                    Comparator.comparing(
                                            (Apuracao a) -> a.fila.peekFirst(),
                                            MAIS_VOTADO_PRIMEIRO))
                            .orElse(null);
            if (melhor == null) {
                return;
            }
            melhor.cadeirasPorSobras++;
            eleitos.add(melhor.eleger(CriterioEleicao.MAIS_VOTADOS));
        }
    }

    private static DistribuicaoCadeiras montar(
            long votosValidos,
            int vagas,
            long qe,
            boolean nenhumAtingiu,
            List<Apuracao> apuracoes,
            List<RodadaSobra> rodadas,
            List<CandidatoEleito> eleitos) {
        List<ResultadoPartido> resultados =
                apuracoes.stream()
                        .map(
                                a ->
                                        new ResultadoPartido(
                                                a.partido,
                                                a.quocientePartidario,
                                                a.cadeirasPorQuociente,
                                                a.cadeirasPorSobras,
                                                a.atingiuClausulaDesempenho()))
                        .toList();
        return new DistribuicaoCadeiras(
                votosValidos, vagas, qe, nenhumAtingiu, resultados, rodadas, eleitos);
    }

    private static void validar(int vagas, List<PartidoProporcional> partidos) {
        quocienteEleitoral(0, vagas); // valida as vagas num só lugar
        if (partidos == null || partidos.isEmpty() || partidos.stream().anyMatch(Objects::isNull)) {
            throw new DadosInvalidosException("Informe ao menos um partido.");
        }
        Set<String> siglas = new HashSet<>();
        for (PartidoProporcional p : partidos) {
            if (!siglas.add(p.sigla().toLowerCase(Locale.ROOT))) {
                throw new DadosInvalidosException("Sigla de partido repetida: " + p.sigla() + ".");
            }
        }
    }

    /** Estado mutável de um partido durante a apuração; nunca sai desta classe. */
    private static final class Apuracao {
        private final PartidoProporcional partido;
        private final long qe;
        private final long quocientePartidario;
        private final Deque<CandidatoProporcional> fila;
        private int cadeirasPorQuociente;
        private int cadeirasPorSobras;

        Apuracao(PartidoProporcional partido, long qe) {
            this.partido = partido;
            this.qe = qe;
            this.quocientePartidario = quocientePartidario(partido.votosTotais(), qe);
            this.fila =
                    new ArrayDeque<>(
                            partido.candidatos().stream().sorted(MAIS_VOTADO_PRIMEIRO).toList());
        }

        long votos() {
            return partido.votosTotais();
        }

        int cadeiras() {
            return cadeirasPorQuociente + cadeirasPorSobras;
        }

        /** Partido com ao menos 80% do QE (art. 109, §2º): 5 * votos >= 4 * QE. */
        boolean atingiuClausulaDesempenho() {
            return 5 * votos() >= 4 * qe;
        }

        /**
         * Se o próximo da fila tem ao menos {@code percentual}% do QE. Basta olhar o primeiro: a
         * fila está em ordem decrescente de votos.
         */
        boolean temCandidatoCom(int percentual) {
            CandidatoProporcional proximo = fila.peekFirst();
            return proximo != null && 100 * proximo.votos() >= percentual * qe;
        }

        CandidatoEleito eleger(CriterioEleicao criterio) {
            CandidatoProporcional c = fila.removeFirst();
            return new CandidatoEleito(c.nome(), partido.sigla(), c.votos(), criterio);
        }

        MediaPartido media() {
            BigDecimal media =
                    BigDecimal.valueOf(votos())
                            .divide(BigDecimal.valueOf(cadeiras() + 1L), 2, RoundingMode.HALF_UP);
            return new MediaPartido(partido.sigla(), votos(), cadeiras(), media);
        }

        /** Compara votos/(cadeiras+1) por produto cruzado, sem divisão. */
        static int compararMedia(Apuracao a, Apuracao b) {
            int porMedia =
                    Long.compare(a.votos() * (b.cadeiras() + 1L), b.votos() * (a.cadeiras() + 1L));
            if (porMedia != 0) {
                return porMedia;
            }
            int porVotos = Long.compare(a.votos(), b.votos());
            if (porVotos != 0) {
                return porVotos;
            }
            return b.partido.sigla().compareTo(a.partido.sigla());
        }
    }
}
