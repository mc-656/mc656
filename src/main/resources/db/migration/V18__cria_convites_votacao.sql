-- Convite de eleitores por link, e-mail ou QR Code (RF-17, RF-02).
-- Um convite por votação: o link é da votação, e trocar o modo não deve invalidar o que já circulou.
CREATE TABLE convites_votacao (
    votacao_id UUID PRIMARY KEY REFERENCES votacoes (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    modo       VARCHAR(10) NOT NULL CHECK (modo IN ('ABERTO', 'RESTRITO')),
    criado_em  TIMESTAMPTZ NOT NULL
);

-- Lista de e-mails convidados. No modo RESTRITO, só eles entram pelo link.
-- Guardados normalizados (minúsculas, sem espaços) para comparar com o e-mail do usuário.
CREATE TABLE convites_emails (
    votacao_id UUID         NOT NULL REFERENCES convites_votacao (votacao_id) ON DELETE CASCADE,
    email      VARCHAR(255) NOT NULL,
    PRIMARY KEY (votacao_id, email)
);
