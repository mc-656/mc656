-- RF-12: opções votáveis de uma eleição privada. A UNIQUE começa por votacao_id e também atende
-- a listagem dos candidatos de uma votação.
CREATE TABLE candidatos_chapas (
    id UUID PRIMARY KEY,
    votacao_id UUID NOT NULL REFERENCES votacoes (id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT candidatos_chapas_nome_unico UNIQUE (votacao_id, nome)
);
