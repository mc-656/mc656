package com.unicamp.engsoft.eleicao.eleicaopublica.domain;

/** Candidato que ocupou uma cadeira, com o critério que o elegeu (RF-32). */
public record CandidatoEleito(String nome, String partido, long votos, CriterioEleicao criterio) {}
