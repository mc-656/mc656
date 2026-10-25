package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

/** Por qual regra do Código Eleitoral uma cadeira foi preenchida (RF-32). */
public enum CriterioEleicao {
    /** Vaga garantida pelo quociente partidário (arts. 107 e 108). */
    QUOCIENTE_PARTIDARIO,
    /** Sobra por maiores médias, respeitando a cláusula de desempenho 80/20 (art. 109, I e II). */
    SOBRA_MAIORES_MEDIAS,
    /**
     * Sobra por maiores médias entre todos os partidos, quando nenhum cumpre mais a cláusula 80/20
     * (art. 109, III, como interpretado pelo STF nas ADIs 7228, 7263 e 7325).
     */
    SOBRA_SEM_CLAUSULA,
    /** Nenhum partido atingiu o quociente eleitoral: entram os mais votados (art. 111). */
    MAIS_VOTADOS
}
