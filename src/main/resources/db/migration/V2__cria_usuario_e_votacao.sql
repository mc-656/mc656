CREATE TABLE IF NOT EXISTS usuarios (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(320) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uk_usuarios_username UNIQUE (username),
    CONSTRAINT uk_usuarios_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS usuario_papeis (
    usuario_id UUID NOT NULL,
    papel VARCHAR(30) NOT NULL,
    PRIMARY KEY (usuario_id, papel),
    CONSTRAINT fk_usuario_papeis_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS votacoes (
    id UUID PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    inicio_em TIMESTAMPTZ NOT NULL,
    fim_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'RascunhoVotacao'
);