CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE papel_usuario AS ENUM ('ELEITOR', 'ADMIN_VOTACAO');

CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    papel papel_usuario NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    modificado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email ON usuarios (email);