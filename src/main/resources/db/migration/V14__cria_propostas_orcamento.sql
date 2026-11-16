-- RF-20: propostas do orçamento participativo e o orçamento total disponível.
-- O orçamento fica em votacoes (nulo até o organizador defini-lo) porque é um atributo único da
-- votação, não da regra de apuração (SPECS §4.1). NUMERIC evita erro de arredondamento em dinheiro.
ALTER TABLE votacoes
    ADD COLUMN orcamento_total NUMERIC(15, 2),
    ADD CONSTRAINT votacoes_orcamento_total_positivo CHECK (orcamento_total > 0);

-- A UNIQUE começa por votacao_id e também atende a listagem das propostas de uma votação.
CREATE TABLE propostas (
    id UUID PRIMARY KEY,
    votacao_id UUID NOT NULL REFERENCES votacoes (id) ON DELETE CASCADE,
    titulo VARCHAR(100) NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    custo NUMERIC(15, 2) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT propostas_custo_positivo CHECK (custo > 0),
    CONSTRAINT propostas_titulo_unico UNIQUE (votacao_id, titulo)
);
