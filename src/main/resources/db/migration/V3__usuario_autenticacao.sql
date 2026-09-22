-- Correção da tabela usuario
CREATE TYPE papel_usuario AS ENUM ('eleitor', 'admin_votacao');
ALTER TABLE usuarios ALTER COLUMN papel TYPE papel_usuario;

ALTER TABLE usuarios RENAME COLUMN senha TO senha_hash;

ALTER TABLE usuarios
ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
ADD COLUMN modificado_em TIMESTAMPTZ NOT NULL DEFAULT now();

DROP TABLE usuario_papeis;

CREATE INDEX email ON usuarios (email);