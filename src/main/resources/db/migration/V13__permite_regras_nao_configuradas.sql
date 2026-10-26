-- Votacoes em rascunho podem ainda nao ter regras de apuracao configuradas.
ALTER TABLE votacoes
    ALTER COLUMN tipo_desempate DROP NOT NULL;
