-- RF-10/RF-11: o peso do voto ponderado fica no papel ELEITOR, não em RegraVotacao.
-- DEFAULT 1 cobre as linhas existentes e os papéis ADMIN_VOTACAO, para os quais o peso
-- não tem significado. O CHECK repete no banco a validação do EleitorService.
ALTER TABLE papeis_votacao
    ADD COLUMN peso NUMERIC NOT NULL DEFAULT 1,
    ADD CONSTRAINT papeis_votacao_peso_nao_negativo CHECK (peso >= 0);
