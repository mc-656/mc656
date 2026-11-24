-- A reserva V9 da issue #50 ficou atrás de migrações já aplicadas. Usar nova versão.
ALTER TABLE votacoes ADD COLUMN votacao_origem_id UUID REFERENCES votacoes(id);
ALTER TABLE votacoes ADD CONSTRAINT votacoes_segundo_turno_unico UNIQUE (votacao_origem_id);
ALTER TABLE votacoes ADD CONSTRAINT votacoes_origem_distinta CHECK (votacao_origem_id <> id);
ALTER TABLE votacoes ALTER COLUMN inicio_em DROP NOT NULL;
ALTER TABLE votacoes ALTER COLUMN fim_em DROP NOT NULL;
-- Só a votação derivada em rascunho pode aguardar definição de datas.
ALTER TABLE votacoes ADD CONSTRAINT votacoes_periodo_definido CHECK (
    (inicio_em IS NOT NULL AND fim_em IS NOT NULL)
    OR (votacao_origem_id IS NOT NULL AND estado = 'RascunhoVotacao'
        AND inicio_em IS NULL AND fim_em IS NULL)
);
ALTER TABLE candidatos_chapas ADD COLUMN data_nascimento DATE;

CREATE TABLE apuracoes (
    votacao_id UUID PRIMARY KEY REFERENCES votacoes(id),
    concluida_em TIMESTAMPTZ NOT NULL,
    regra JSONB NOT NULL,
    resultado JSONB NOT NULL,
    total_cedulas BIGINT NOT NULL CHECK (total_cedulas >= 0),
    segundo_turno_id UUID UNIQUE REFERENCES votacoes(id),
    CHECK (segundo_turno_id <> votacao_id)
);
