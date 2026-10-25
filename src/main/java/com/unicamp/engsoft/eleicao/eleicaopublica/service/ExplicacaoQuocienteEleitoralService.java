package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CalculadoraQuocienteEleitoral;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoEleito;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.CandidatoProporcional;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.DistribuicaoCadeiras;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.PartidoProporcional;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.ResultadoPartido;
import com.unicamp.engsoft.eleicao.eleicaopublica.domain.RodadaSobra;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.CandidatoExemplo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.CandidatoResumo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.CasoContraIntuitivo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.Exemplo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.FonteLegal;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.PartidoExemplo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.ExplicacaoQuocienteEleitoralResponse.SecaoDidatica;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Monta a explicação didática do quociente eleitoral (RF-32, RNF-07).
 *
 * <p>Os números do exemplo não são escritos à mão no texto: saem da {@link
 * CalculadoraQuocienteEleitoral}, para que explicação e regra nunca divirjam. O cenário foi
 * escolhido para que um candidato com 5 mil votos seja eleito enquanto outro com 13 mil fica de
 * fora, que é a dúvida que motivou o projeto (E4 na elicitação).
 *
 * <p>Como o conteúdo é fixo, a resposta é calculada uma única vez, na construção do bean.
 */
@Service
public class ExplicacaoQuocienteEleitoralService {

    static final String URL_CODIGO_ELEITORAL =
            "https://www.planalto.gov.br/ccivil_03/leis/l4737compilado.htm";
    static final String URL_LEI_14211 =
            "https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2021/lei/l14211.htm";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ExplicacaoQuocienteEleitoralResponse explicacao;

    public ExplicacaoQuocienteEleitoralService() {
        this.explicacao = montar();
    }

    public ExplicacaoQuocienteEleitoralResponse explicar() {
        return explicacao;
    }

    /** Cenário fictício: 5 vagas e 100 mil votos válidos, divididos entre quatro partidos. */
    static List<PartidoProporcional> partidosDoExemplo() {
        return List.of(
                new PartidoProporcional(
                        "Partido A",
                        1_000,
                        List.of(
                                new CandidatoProporcional("Ana", 26_000),
                                new CandidatoProporcional("Bruno", 9_000),
                                new CandidatoProporcional("Carla", 5_000),
                                new CandidatoProporcional("Davi", 4_000))),
                new PartidoProporcional(
                        "Partido B",
                        1_000,
                        List.of(
                                new CandidatoProporcional("Diego", 14_000),
                                new CandidatoProporcional("Elisa", 8_000),
                                new CandidatoProporcional("Fábio", 2_000))),
                new PartidoProporcional(
                        "Partido C",
                        500,
                        List.of(
                                new CandidatoProporcional("Gabriela", 13_000),
                                new CandidatoProporcional("Hugo", 1_500))),
                new PartidoProporcional(
                        "Partido D",
                        500,
                        List.of(
                                new CandidatoProporcional("Igor", 12_000),
                                new CandidatoProporcional("Júlia", 2_500))));
    }

    static final int VAGAS_DO_EXEMPLO = 5;

    private static ExplicacaoQuocienteEleitoralResponse montar() {
        DistribuicaoCadeiras d =
                CalculadoraQuocienteEleitoral.distribuir(VAGAS_DO_EXEMPLO, partidosDoExemplo());
        if (d.nenhumPartidoAtingiuQuociente()) {
            // O texto descreve o caminho normal (QP + sobras); o art. 111 é só citado.
            throw new IllegalStateException("O exemplo didático precisa de partidos acima do QE.");
        }
        long qe = d.quocienteEleitoral();
        long minimoQuociente = (qe + 9) / 10;
        long minimoPartidoSobras = (4 * qe + 4) / 5;
        long minimoCandidatoSobras = (qe + 4) / 5;
        CasoContraIntuitivo caso = casoContraIntuitivo(d);

        Exemplo exemplo =
                new Exemplo(
                        d.vagas(),
                        d.votosValidos(),
                        qe,
                        n(d.votosValidos()) + " ÷ " + d.vagas() + " = " + n(qe),
                        minimoQuociente,
                        minimoPartidoSobras,
                        minimoCandidatoSobras,
                        d.partidos().stream().map(r -> partido(r, d)).toList(),
                        d.rodadasSobras(),
                        d.eleitos(),
                        caso,
                        passos(d, minimoQuociente, minimoPartidoSobras, minimoCandidatoSobras));

        return new ExplicacaoQuocienteEleitoralResponse(
                "Como funciona o quociente eleitoral",
                "Deputados e vereadores são eleitos pelo sistema proporcional: primeiro se"
                        + " decide quantas cadeiras cada partido ganha e só depois quem as ocupa.",
                secoes(d, caso, minimoQuociente, minimoPartidoSobras, minimoCandidatoSobras),
                exemplo,
                baseLegal());
    }

    private static List<SecaoDidatica> secoes(
            DistribuicaoCadeiras d,
            CasoContraIntuitivo caso,
            long minimoQuociente,
            long minimoPartidoSobras,
            long minimoCandidatoSobras) {
        long qe = d.quocienteEleitoral();
        return List.of(
                new SecaoDidatica(
                        "O voto vai primeiro para o partido",
                        List.of(
                                "Na eleição de deputados e vereadores, todo voto válido conta para"
                                        + " um partido: o voto no número do candidato e o voto só"
                                        + " na legenda.",
                                "As cadeiras são divididas entre os partidos conforme a soma"
                                        + " desses votos. Dentro de cada partido, entram os"
                                        + " candidatos mais votados.",
                                "Votos brancos e nulos não são válidos e não entram na conta.")),
                new SecaoDidatica(
                        "Passo 1: quociente eleitoral",
                        List.of(
                                "O quociente eleitoral (QE) é quantos votos \"valem\" uma cadeira."
                                        + " Ele é o total de votos válidos dividido pelo número"
                                        + " de vagas.",
                                "Se a divisão não for exata, a fração maior que meio arredonda"
                                        + " para cima; a igual ou menor que meio é desprezada"
                                        + " (art. 106 do Código Eleitoral).",
                                "No exemplo: "
                                        + n(d.votosValidos())
                                        + " votos válidos ÷ "
                                        + d.vagas()
                                        + " vagas = QE de "
                                        + n(qe)
                                        + " votos.")),
                new SecaoDidatica(
                        "Passo 2: quociente partidário",
                        List.of(
                                "O quociente partidário (QP) é quantas vezes o partido atingiu o"
                                        + " QE: votos do partido ÷ QE, desprezando a fração (art."
                                        + " 107).",
                                "Cada partido ganha QP cadeiras, ocupadas pelos seus mais votados."
                                        + " Mas só entra quem tiver ao menos 10% do QE (art. 108):"
                                        + " no exemplo, "
                                        + n(minimoQuociente)
                                        + " votos.")),
                new SecaoDidatica(
                        "Passo 3: distribuição das sobras",
                        List.of(
                                "As cadeiras que sobram são distribuídas uma a uma pelas maiores"
                                        + " médias (art. 109).",
                                "A média de um partido é seus votos ÷ (cadeiras que já tem + 1)."
                                        + " Quem tiver a maior média leva a vaga, e a conta se"
                                        + " repete até acabarem as sobras.")),
                new SecaoDidatica(
                        "Cláusula de desempenho (80% e 20%)",
                        List.of(
                                "Desde a Lei 14.211/2021, só disputam as sobras partidos com ao"
                                        + " menos 80% do QE e candidatos com ao menos 20% dele.",
                                "No exemplo, o partido precisa de "
                                        + n(minimoPartidoSobras)
                                        + " votos e o candidato, de "
                                        + n(minimoCandidatoSobras)
                                        + ".",
                                "Se nenhum partido cumprir mais esses mínimos, as vagas restantes"
                                        + " vão pelas maiores médias entre todos os partidos,"
                                        + " como decidiu o STF em 2024 (ADIs 7228, 7263 e 7325).")),
                new SecaoDidatica(
                        "Por que um candidato com menos votos pode ser eleito",
                        List.of(
                                caso.explicacao(),
                                "Ou seja: a votação do partido decide quantas cadeiras ele"
                                        + " recebe. O voto do candidato só decide a ordem dentro"
                                        + " do partido.")));
    }

    private static List<String> passos(
            DistribuicaoCadeiras d,
            long minimoQuociente,
            long minimoPartidoSobras,
            long minimoCandidatoSobras) {
        long qe = d.quocienteEleitoral();
        List<String> passos = new ArrayList<>();
        passos.add(
                "Votos válidos: "
                        + n(d.votosValidos())
                        + ". Vagas: "
                        + d.vagas()
                        + ". QE = "
                        + n(d.votosValidos())
                        + " ÷ "
                        + d.vagas()
                        + " = "
                        + n(qe)
                        + ".");
        for (ResultadoPartido r : d.partidos()) {
            passos.add(
                    r.partido().sigla()
                            + ": "
                            + n(r.partido().votosTotais())
                            + " ÷ "
                            + n(qe)
                            + " → QP = "
                            + r.quocientePartidario()
                            + " (cadeiras pelo quociente: "
                            + r.cadeirasPorQuociente()
                            + ").");
        }
        int restantes =
                d.vagas()
                        - d.partidos().stream()
                                .mapToInt(ResultadoPartido::cadeirasPorQuociente)
                                .sum();
        String fora =
                d.partidos().stream()
                        .filter(r -> !r.atingiuClausulaDesempenho())
                        .map(r -> r.partido().sigla())
                        .collect(Collectors.joining(" e "));
        passos.add(
                (restantes == 1 ? "Sobra 1 vaga" : "Sobram " + restantes + " vagas")
                        + ". Só disputam partidos com ao menos "
                        + n(minimoPartidoSobras)
                        + " votos e candidatos com ao menos "
                        + n(minimoCandidatoSobras)
                        + (fora.isEmpty() ? "." : "; ficam de fora: " + fora + "."));
        for (RodadaSobra rodada : d.rodadasSobras()) {
            String medias =
                    rodada.medias().stream()
                            .map(
                                    m ->
                                            m.partido()
                                                    + " "
                                                    + n(m.votos())
                                                    + " ÷ "
                                                    + (m.cadeirasAntes() + 1)
                                                    + " = "
                                                    + m.media().toPlainString())
                            .collect(Collectors.joining("; "));
            CandidatoEleito e = rodada.eleito();
            passos.add(
                    "Sobra "
                            + rodada.numero()
                            + ": "
                            + medias
                            + (rodada.clausulaDesempenho()
                                    ? ". Maior média: "
                                    : ". Ninguém mais cumpre a cláusula 80/20; maior média"
                                            + " entre todos: ")
                            + e.partido()
                            + ", que elege "
                            + e.nome()
                            + " ("
                            + n(e.votos())
                            + " votos).");
        }
        passos.add(
                "Eleitos: "
                        + d.eleitos().stream()
                                .map(e -> e.nome() + " (" + e.partido() + ", " + n(e.votos()) + ")")
                                .collect(Collectors.joining(", "))
                        + ".");
        return passos;
    }

    private static PartidoExemplo partido(ResultadoPartido r, DistribuicaoCadeiras d) {
        PartidoProporcional p = r.partido();
        List<CandidatoExemplo> candidatos =
                p.candidatos().stream()
                        .sorted(CandidatoProporcional.MAIS_VOTADO_PRIMEIRO)
                        .map(c -> new CandidatoExemplo(c.nome(), c.votos(), eleito(d, p, c)))
                        .toList();
        return new PartidoExemplo(
                p.sigla(),
                p.votosLegenda(),
                p.votosNominais(),
                p.votosTotais(),
                r.quocientePartidario(),
                n(p.votosTotais())
                        + " ÷ "
                        + n(d.quocienteEleitoral())
                        + " = "
                        + r.quocientePartidario()
                        + " (fração desprezada)",
                r.cadeirasPorQuociente(),
                r.cadeirasPorSobras(),
                r.cadeirasTotais(),
                r.atingiuClausulaDesempenho(),
                candidatos);
    }

    private static boolean eleito(
            DistribuicaoCadeiras d, PartidoProporcional p, CandidatoProporcional c) {
        return d.eleitos().stream()
                .anyMatch(e -> e.partido().equals(p.sigla()) && e.nome().equals(c.nome()));
    }

    /**
     * Compara o eleito menos votado com o não eleito mais votado. Falha alto se o cenário deixar de
     * ilustrar o caso, porque aí a explicação inteira perderia o sentido.
     */
    private static CasoContraIntuitivo casoContraIntuitivo(DistribuicaoCadeiras d) {
        CandidatoEleito menosVotado =
                d.eleitos().stream()
                        .min(Comparator.comparingLong(CandidatoEleito::votos))
                        .orElseThrow();
        CandidatoResumo maisVotadoFora =
                d.partidos().stream()
                        .flatMap(
                                r ->
                                        r.partido().candidatos().stream()
                                                .filter(c -> !eleito(d, r.partido(), c))
                                                .map(
                                                        c ->
                                                                new CandidatoResumo(
                                                                        c.nome(),
                                                                        r.partido().sigla(),
                                                                        c.votos())))
                        .max(Comparator.comparingLong(CandidatoResumo::votos))
                        .orElseThrow();
        if (maisVotadoFora.votos() <= menosVotado.votos()) {
            throw new IllegalStateException(
                    "O exemplo didático deixou de mostrar um eleito com menos votos.");
        }
        ResultadoPartido partidoFora =
                d.partidos().stream()
                        .filter(r -> r.partido().sigla().equals(maisVotadoFora.partido()))
                        .findFirst()
                        .orElseThrow();
        String motivo =
                partidoFora.quocientePartidario() == 0 && !partidoFora.atingiuClausulaDesempenho()
                        ? " votos: não atingiu o QE nem os 80% exigidos para disputar as sobras."
                        : " votos, e as cadeiras que conquistou ficaram com candidatos à frente na"
                                + " disputa.";
        String explicacao =
                menosVotado.nome()
                        + " ("
                        + menosVotado.partido()
                        + ") se elegeu com "
                        + n(menosVotado.votos())
                        + " votos, e "
                        + maisVotadoFora.nome()
                        + " ("
                        + maisVotadoFora.partido()
                        + ") ficou de fora com "
                        + n(maisVotadoFora.votos())
                        + ". O "
                        + maisVotadoFora.partido()
                        + " somou "
                        + n(partidoFora.partido().votosTotais())
                        + motivo;
        return new CasoContraIntuitivo(
                new CandidatoResumo(menosVotado.nome(), menosVotado.partido(), menosVotado.votos()),
                maisVotadoFora,
                explicacao);
    }

    private static List<FonteLegal> baseLegal() {
        return List.of(
                new FonteLegal(
                        "Código Eleitoral (Lei nº 4.737/1965)",
                        "Arts. 106 a 109 e 111",
                        URL_CODIGO_ELEITORAL),
                new FonteLegal(
                        "Lei nº 14.211/2021",
                        "Altera os arts. 108 e 109: cláusula de desempenho de 80% e 20%",
                        URL_LEI_14211));
    }

    private static String n(long valor) {
        // NumberFormat não é thread-safe; criar a cada uso evita estado compartilhado.
        return NumberFormat.getIntegerInstance(PT_BR).format(valor);
    }
}
