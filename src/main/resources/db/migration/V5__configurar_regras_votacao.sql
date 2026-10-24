-- RF-11: regras de apuração embutidas no agregado votação (RegraVotacao).
-- As colunas ficam nullable de propósito: um rascunho nasce sem regras e a
-- GuardaRegrasConfiguradas só permite publicar depois que forem configuradas.
-- O peso de cada eleitor não fica aqui: é coluna de papeis_votacao.
ALTER TABLE votacoes
    ADD COLUMN tipo_maioria VARCHAR(30),
    ADD COLUMN percentual_qualificado NUMERIC,
    ADD COLUMN segundo_turno BOOLEAN,
    ADD COLUMN quorum_minimo_percentual NUMERIC,
    ADD COLUMN voto_ponderado BOOLEAN;
