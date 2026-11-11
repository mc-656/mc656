package com.unicamp.engsoft.eleicao.eleicaopublica.service;

import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse.Cargo;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.CargosResponse.Sistema;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.TiposVotoResponse;
import com.unicamp.engsoft.eleicao.eleicaopublica.dto.TiposVotoResponse.TipoVoto;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Conteúdo didático estático sobre tipos de voto (#91) e cargos e chapas (#92), parte de RF-32.
 *
 * <p>O texto mora no código, e não no banco, <em>porque</em> é igual para todos, muda só junto com
 * a legislação e precisa passar por revisão de código como qualquer regra. Página e API leem daqui,
 * então nunca divergem. As respostas são imutáveis e montadas uma única vez.
 *
 * <p>Linguagem simples e parágrafos curtos de propósito: o público inclui eleitores leigos
 * (RNF-07).
 */
@Service
public class ConteudoDidaticoService {

    /** Página da explicação do quociente eleitoral, para onde o voto na legenda remete. */
    public static final String URL_EXPLICACAO_QUOCIENTE = "/eleicoes-publicas/quociente-eleitoral";

    /** Base das âncoras por cargo: {@code /eleicoes-publicas/cargos#<id>}. */
    public static final String URL_PAGINA_CARGOS = "/eleicoes-publicas/cargos";

    static final String URL_TSE = "https://www.tse.jus.br/";
    static final String URL_CONSTITUICAO =
            "https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm";
    static final String URL_LEI_DAS_ELEICOES =
            "https://www.planalto.gov.br/ccivil_03/leis/l9504.htm";
    static final String URL_CODIGO_ELEITORAL =
            "https://www.planalto.gov.br/ccivil_03/leis/l4737compilado.htm";

    private final TiposVotoResponse tiposVoto = montarTiposVoto();
    private final CargosResponse cargos = montarCargos();

    public TiposVotoResponse tiposVoto() {
        return tiposVoto;
    }

    public CargosResponse cargos() {
        return cargos;
    }

    private static TiposVotoResponse montarTiposVoto() {
        List<TipoVoto> tipos =
                List.of(
                        new TipoVoto(
                                "nulo",
                                "Voto nulo",
                                "É o voto em um número que não pertence a nenhum candidato nem"
                                        + " partido. Quem vota nulo manifesta que não quer"
                                        + " escolher ninguém para aquele cargo.",
                                "Digite um número inexistente (por exemplo, 00) e aperte CONFIRMA."
                                        + " A urna avisa que o número está errado e que o voto"
                                        + " será nulo antes de você confirmar.",
                                false,
                                "Não é voto válido: é descartado e não entra em nenhuma conta,"
                                        + " nem na de quem vence nem no quociente eleitoral."
                                        + " Mesmo que a maioria vote nulo, a eleição não é"
                                        + " anulada: a anulação prevista no Código Eleitoral"
                                        + " vale para votos anulados pela Justiça por fraude,"
                                        + " não para votos nulos do eleitor."),
                        new TipoVoto(
                                "branco",
                                "Voto em branco",
                                "É o voto em que o eleitor declara não preferir nenhum dos"
                                        + " candidatos para aquele cargo.",
                                "Aperte a tecla BRANCO e depois CONFIRMA. A urna mostra"
                                        + " \"VOTO EM BRANCO\" antes da confirmação.",
                                false,
                                "Não é voto válido. Desde a Constituição de 1988 o branco não vai"
                                        + " para quem está ganhando: tem o mesmo efeito prático"
                                        + " do nulo. Fica de fora da maioria absoluta exigida"
                                        + " nos cargos majoritários e do cálculo do quociente"
                                        + " eleitoral."),
                        new TipoVoto(
                                "legenda",
                                "Voto na legenda",
                                "É o voto dado ao partido (ou à federação), e não a um candidato"
                                        + " específico. Só existe nos cargos proporcionais:"
                                        + " deputado federal, deputado estadual ou distrital e"
                                        + " vereador.",
                                "Digite apenas os dois primeiros números, que identificam o"
                                        + " partido, confira o nome do partido na tela e aperte"
                                        + " CONFIRMA. Se você digitar o número completo de um"
                                        + " candidato que não existe, mas com o número do"
                                        + " partido correto, o voto também vai para a legenda.",
                                true,
                                "É voto válido e conta para o partido: soma-se aos votos dos"
                                        + " seus candidatos para definir quantas cadeiras o"
                                        + " partido conquista. Não ajuda nenhum candidato em"
                                        + " particular a subir na lista do partido."));

        TiposVotoResponse.Secao legendaNoQuociente =
                new TiposVotoResponse.Secao(
                        "Como o voto na legenda entra no quociente partidário",
                        List.of(
                                "Nos cargos proporcionais, as cadeiras são divididas entre os"
                                        + " partidos. Primeiro se calcula o quociente eleitoral:"
                                        + " votos válidos divididos pelo número de vagas.",
                                "Depois, os votos de cada partido (votos de legenda mais votos"
                                        + " nominais dos seus candidatos) são divididos pelo"
                                        + " quociente eleitoral. O resultado, sem a parte"
                                        + " decimal, é o quociente partidário: quantas cadeiras"
                                        + " o partido conquista nessa etapa.",
                                "Por isso o voto na legenda pesa tanto quanto um voto nominal"
                                        + " na conta das cadeiras. Quem ocupa as cadeiras são os"
                                        + " candidatos mais votados do partido, desde que cada um"
                                        + " tenha pelo menos 10% do quociente eleitoral.",
                                "Votos nulos e brancos não entram em nenhuma dessas contas."));

        return new TiposVotoResponse(
                "Voto nulo, em branco e na legenda",
                "Na urna você pode votar em um candidato, no partido (legenda), em branco ou"
                        + " anular. Só os votos em candidatos e em legendas são válidos: são eles"
                        + " que decidem o resultado.",
                tipos,
                legendaNoQuociente,
                URL_EXPLICACAO_QUOCIENTE,
                List.of(
                        new TiposVotoResponse.Fonte(
                                "Tribunal Superior Eleitoral (TSE) — fonte oficial", URL_TSE),
                        new TiposVotoResponse.Fonte(
                                "Lei das Eleições (Lei nº 9.504/1997), arts. 2º, 3º, 5º e 59",
                                URL_LEI_DAS_ELEICOES),
                        new TiposVotoResponse.Fonte(
                                "Constituição Federal, art. 77, § 2º", URL_CONSTITUICAO),
                        new TiposVotoResponse.Fonte(
                                "Código Eleitoral (Lei nº 4.737/1965), arts. 106 a 109 e 224",
                                URL_CODIGO_ELEITORAL)));
    }

    private static CargosResponse montarCargos() {
        String chapaComVice =
                "Chapa única com titular e vice, sempre votados juntos: o voto no titular vale"
                        + " também para o vice.";
        String semChapa =
                "Não há vice. Os suplentes são os candidatos mais votados do mesmo partido que"
                        + " não foram eleitos.";

        List<Cargo> lista =
                List.of(
                        cargo(
                                "presidente",
                                "Presidente da República",
                                "Federal",
                                Sistema.MAJORITARIO,
                                "4 anos; pode haver segundo turno",
                                chapaComVice + " O vice é o vice-presidente.",
                                "Chefia o governo federal e representa o Brasil no exterior.",
                                "Sanciona ou veta leis aprovadas pelo Congresso e pode editar"
                                        + " medidas provisórias.",
                                "Nomeia ministros e comanda as Forças Armadas."),
                        cargo(
                                "governador",
                                "Governador",
                                "Estadual",
                                Sistema.MAJORITARIO,
                                "4 anos; pode haver segundo turno",
                                chapaComVice + " O vice é o vice-governador.",
                                "Administra o estado e executa o orçamento estadual.",
                                "Comanda a segurança pública estadual (polícias civil e"
                                        + " militar).",
                                "Sanciona ou veta leis aprovadas pela Assembleia Legislativa."),
                        cargo(
                                "senador",
                                "Senador",
                                "Federal",
                                Sistema.MAJORITARIO,
                                "8 anos; turno único; cada estado e o DF elegem 3 senadores,"
                                        + " renovados de forma alternada (1 e 2 vagas)",
                                "Não tem vice: a chapa é formada pelo titular e dois suplentes,"
                                        + " que assumem se o senador deixar o cargo.",
                                "Representa o estado no Congresso e vota leis federais com a"
                                        + " Câmara.",
                                "Aprova indicações como ministros do STF e embaixadores.",
                                "Julga o presidente em crimes de responsabilidade"
                                        + " (impeachment)."),
                        cargo(
                                "deputado-federal",
                                "Deputado federal",
                                "Federal",
                                Sistema.PROPORCIONAL,
                                "4 anos; turno único",
                                semChapa,
                                "Propõe e vota leis federais e o orçamento da União.",
                                "Fiscaliza o governo federal.",
                                "Autoriza a abertura de processo de impeachment contra o"
                                        + " presidente."),
                        cargo(
                                "deputado-estadual",
                                "Deputado estadual (distrital, no DF)",
                                "Estadual",
                                Sistema.PROPORCIONAL,
                                "4 anos; turno único",
                                semChapa,
                                "Propõe e vota leis estaduais na Assembleia Legislativa.",
                                "Aprova o orçamento do estado.",
                                "Fiscaliza o governador e a administração estadual."),
                        cargo(
                                "prefeito",
                                "Prefeito",
                                "Municipal",
                                Sistema.MAJORITARIO,
                                "4 anos; segundo turno só em municípios com mais de 200 mil"
                                        + " eleitores",
                                chapaComVice + " O vice é o vice-prefeito.",
                                "Administra o município e executa o orçamento municipal.",
                                "Cuida de serviços locais como saúde básica, escolas"
                                        + " municipais, transporte e limpeza urbana.",
                                "Sanciona ou veta leis aprovadas pela Câmara Municipal."),
                        cargo(
                                "vereador",
                                "Vereador",
                                "Municipal",
                                Sistema.PROPORCIONAL,
                                "4 anos; turno único",
                                semChapa,
                                "Propõe e vota leis municipais.",
                                "Aprova o orçamento do município.",
                                "Fiscaliza o prefeito e a administração municipal."));

        List<CargosResponse.Secao> secoes =
                List.of(
                        new CargosResponse.Secao(
                                "chapas",
                                "Como se forma uma chapa",
                                List.of(
                                        "Nos cargos de presidente, governador e prefeito, o"
                                                + " candidato concorre em chapa com um vice. Você"
                                                + " não vota no vice separadamente: ao escolher o"
                                                + " titular, elege os dois.",
                                        "O vice substitui o titular em ausências e assume o cargo"
                                                + " se ele sair de vez (renúncia, morte ou"
                                                + " cassação). Ele pode ser de outro partido"
                                                + " aliado na mesma coligação.",
                                        "Senadores não têm vice: concorrem com dois suplentes."
                                                + " Deputados e vereadores também não têm vice;"
                                                + " quem assume uma vaga aberta é o próximo mais"
                                                + " votado do partido.")),
                        new CargosResponse.Secao(
                                "majoritario-proporcional",
                                "Cargos majoritários e proporcionais",
                                List.of(
                                        "Majoritários (presidente, governador, senador e"
                                                + " prefeito): vence quem tem mais votos. Para"
                                                + " presidente, governador e prefeito de cidades"
                                                + " com mais de 200 mil eleitores, é preciso a"
                                                + " maioria absoluta dos votos válidos; se"
                                                + " ninguém alcançar, há segundo turno entre os"
                                                + " dois mais votados.",
                                        "Proporcionais (deputados e vereadores): as cadeiras são"
                                                + " divididas entre os partidos de acordo com o"
                                                + " total de votos de cada um, pelo quociente"
                                                + " eleitoral. Depois, os candidatos mais votados"
                                                + " de cada partido ocupam as cadeiras"
                                                + " conquistadas.",
                                        "Por isso, num cargo proporcional, um candidato pode ser"
                                                + " eleito com menos votos que outro de um"
                                                + " partido diferente. Desde 2020 não há mais"
                                                + " coligações nas eleições proporcionais;"
                                                + " partidos podem se unir em federações.")));

        return new CargosResponse(
                "Cargos em disputa e formação de chapas",
                "O que faz cada cargo eletivo, como titular e vice formam uma chapa e por que"
                        + " alguns cargos são decididos pela maioria e outros pela divisão"
                        + " proporcional de cadeiras.",
                secoes,
                lista,
                List.of(
                        new CargosResponse.Fonte(
                                "Tribunal Superior Eleitoral (TSE) — fonte oficial", URL_TSE),
                        new CargosResponse.Fonte(
                                "Constituição Federal, arts. 27 a 32, 45 a 52, 77 e 84",
                                URL_CONSTITUICAO),
                        new CargosResponse.Fonte(
                                "Lei das Eleições (Lei nº 9.504/1997)", URL_LEI_DAS_ELEICOES)));
    }

    /** Recebe as atribuições como varargs para deixar visível no código o limite de 3 tópicos. */
    private static Cargo cargo(
            String id,
            String nome,
            String esfera,
            Sistema sistema,
            String mandato,
            String composicaoChapa,
            String... atribuicoes) {
        if (atribuicoes.length == 0 || atribuicoes.length > 3) {
            throw new IllegalStateException("Cargo " + id + " deve ter de 1 a 3 atribuições");
        }
        return new Cargo(
                id,
                nome,
                esfera,
                sistema,
                mandato,
                composicaoChapa,
                List.of(atribuicoes),
                URL_PAGINA_CARGOS + "#" + id);
    }
}
