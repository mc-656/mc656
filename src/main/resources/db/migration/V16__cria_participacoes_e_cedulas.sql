-- RF-13/RF-14: o voto é separado em duas tabelas append-only para o sigilo (RNF-02) valer também
-- para quem lê o banco. A V8 reservada na #57 acabou usada por outra migração e V14/V15 estão
-- reservadas por PRs paralelos; por isso V16.
--
-- Risco residual conhecido: as duas linhas são gravadas na mesma transação (atomicidade exigida
-- pela #48), então colunas de sistema do Postgres (xmin, ordem física/ctid) permitem a quem tem
-- acesso direto ao banco correlacioná-las. O sigilo vale para a API e para o modelo de dados; contra
-- um DBA exige mecanismo de desacoplamento próprio (ex.: gravação em lote embaralhada), fora do
-- escopo desta migração.
--
-- participacoes: QUEM votou. A UNIQUE é a barreira final contra voto duplicado (RF-14) quando duas
-- requisições do mesmo eleitor passam juntas pela checagem do service.
CREATE TABLE participacoes (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    votacao_id UUID NOT NULL REFERENCES votacoes (id),
    CONSTRAINT participacoes_eleitor_unico UNIQUE (usuario_id, votacao_id)
);

-- cedulas: EM QUÊ se votou. Sem usuario_id, sem FK para participacoes, sem created_at e sem id
-- sequencial: nada em comum com participacoes que permita cruzar as duas tabelas. O peso é cópia
-- do papel ELEITOR no momento do voto (V12).
CREATE TABLE cedulas (
    id UUID PRIMARY KEY,
    votacao_id UUID NOT NULL REFERENCES votacoes (id),
    opcao_id UUID NOT NULL REFERENCES candidatos_chapas (id),
    peso NUMERIC NOT NULL,
    CONSTRAINT cedulas_peso_nao_negativo CHECK (peso >= 0)
);

-- A apuração lê as cédulas por votação.
CREATE INDEX cedulas_votacao ON cedulas (votacao_id);

-- Append-only no próprio banco (SPECS §7.3): correção de voto é regra de negócio nova, nunca
-- edição de dado histórico. As FKs sem ON DELETE CASCADE já impedem apagar votação, usuário ou
-- opção com voto registrado; o trigger cobre UPDATE/DELETE diretos.
CREATE FUNCTION recusa_alteracao_de_voto() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Tabela % é append-only: % não é permitido.', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER participacoes_append_only
    BEFORE UPDATE OR DELETE ON participacoes
    FOR EACH ROW EXECUTE FUNCTION recusa_alteracao_de_voto();

CREATE TRIGGER cedulas_append_only
    BEFORE UPDATE OR DELETE ON cedulas
    FOR EACH ROW EXECUTE FUNCTION recusa_alteracao_de_voto();
