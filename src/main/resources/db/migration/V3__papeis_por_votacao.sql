-- RF-03: papéis passam a valer por votação. Todo usuário cadastrado é usuário comum;
-- ELEITOR e ADMIN_VOTACAO só existem em relação a uma votação específica.
ALTER TABLE usuarios DROP COLUMN papel;

CREATE TABLE papeis_votacao (
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    votacao_id UUID NOT NULL REFERENCES votacoes (id) ON DELETE CASCADE,
    papel papel_usuario NOT NULL,
    PRIMARY KEY (usuario_id, votacao_id, papel)
);

-- A PK já cobre buscas por usuario_id; este índice atende as buscas por votação.
CREATE INDEX papeis_votacao_votacao ON papeis_votacao (votacao_id);
