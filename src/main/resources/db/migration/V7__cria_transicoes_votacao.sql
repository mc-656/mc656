-- RNF-03: auditoria das transições de estado da votação (#24). Append-only.
CREATE TABLE transicoes_votacao (
    id UUID PRIMARY KEY,
    votacao_id UUID NOT NULL REFERENCES votacoes (id),
    estado_anterior VARCHAR(30) NOT NULL,
    estado_novo VARCHAR(30) NOT NULL,
    autor_id UUID REFERENCES usuarios (id),
    origem VARCHAR(10) NOT NULL,
    motivo VARCHAR(500),
    ocorrida_em TIMESTAMPTZ NOT NULL,
    -- Transição por data (job) não tem autor; transição pedida por usuário sempre tem.
    CONSTRAINT transicoes_votacao_autor_por_origem CHECK (
        (origem = 'SISTEMA' AND autor_id IS NULL) OR (origem = 'USUARIO' AND autor_id IS NOT NULL)
    )
);

CREATE INDEX transicoes_votacao_votacao ON transicoes_votacao (votacao_id, ocorrida_em);
