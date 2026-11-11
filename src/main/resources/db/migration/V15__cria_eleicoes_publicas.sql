-- RF-30/RF-31/RF-33: módulo informativo de eleições públicas.
--
-- Tabelas próprias, sem nenhuma FK para votacoes/candidatos_chapas: o módulo é só leitura de dados
-- externos (TSE, imprensa) e não pode se misturar com os votos das votações privadas.
--
-- Toda informação exibida carrega fonte_nome, fonte_url e atualizado_em (critério da issue #87):
-- o eleitor precisa saber de onde veio o dado e se ele está desatualizado.

CREATE TABLE eleicoes_publicas (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    ano INTEGER NOT NULL,
    data_primeiro_turno DATE NOT NULL,
    abrangencia VARCHAR(100) NOT NULL,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE cargos_publicos (
    id UUID PRIMARY KEY,
    eleicao_id UUID NOT NULL REFERENCES eleicoes_publicas (id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    ordem INTEGER NOT NULL,
    CONSTRAINT cargos_publicos_nome_unico UNIQUE (eleicao_id, nome)
);

CREATE TABLE candidatos_publicos (
    id UUID PRIMARY KEY,
    cargo_id UUID NOT NULL REFERENCES cargos_publicos (id) ON DELETE CASCADE,
    nome_urna VARCHAR(100) NOT NULL,
    nome_completo VARCHAR(200) NOT NULL,
    numero VARCHAR(10) NOT NULL,
    partido_sigla VARCHAR(20) NOT NULL,
    partido_nome VARCHAR(150) NOT NULL,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT candidatos_publicos_numero_unico UNIQUE (cargo_id, numero)
);

CREATE INDEX idx_candidatos_publicos_cargo ON candidatos_publicos (cargo_id);

CREATE TABLE cargos_anteriores_candidato (
    id UUID PRIMARY KEY,
    candidato_id UUID NOT NULL REFERENCES candidatos_publicos (id) ON DELETE CASCADE,
    cargo VARCHAR(150) NOT NULL,
    ano_inicio INTEGER NOT NULL,
    ano_fim INTEGER,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT cargos_anteriores_periodo_valido CHECK (ano_fim IS NULL OR ano_fim >= ano_inicio)
);

CREATE INDEX idx_cargos_anteriores_candidato ON cargos_anteriores_candidato (candidato_id);

CREATE TABLE propostas_candidato (
    id UUID PRIMARY KEY,
    candidato_id UUID NOT NULL REFERENCES candidatos_publicos (id) ON DELETE CASCADE,
    tema VARCHAR(60) NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    descricao TEXT NOT NULL,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_propostas_candidato ON propostas_candidato (candidato_id);

CREATE TABLE noticias_candidato (
    id UUID PRIMARY KEY,
    candidato_id UUID NOT NULL REFERENCES candidatos_publicos (id) ON DELETE CASCADE,
    titulo VARCHAR(250) NOT NULL,
    resumo TEXT NOT NULL,
    publicada_em DATE NOT NULL,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_noticias_candidato ON noticias_candidato (candidato_id);

-- Seed de EXEMPLO. Todos os nomes, partidos, propostas e notícias abaixo são fictícios e estão
-- marcados como tal; servem para demonstrar o módulo até a importação dos dados abertos do TSE.
-- Os UUIDs são fixos para que testes e links de demonstração sejam estáveis.

INSERT INTO eleicoes_publicas (id, nome, tipo, ano, data_primeiro_turno, abrangencia, fonte_nome, fonte_url, atualizado_em)
VALUES ('00000000-0000-4000-8000-000000000001',
        'Eleições Municipais 2028 de Cidade Exemplo (fictícia)', 'MUNICIPAL', 2028, '2028-10-01',
        'Cidade Exemplo (município fictício)',
        'TSE — Calendário Eleitoral (dado de exemplo)', 'https://www.tse.jus.br/eleicoes/calendario-eleitoral',
        '2026-11-01T12:00:00Z');

INSERT INTO cargos_publicos (id, eleicao_id, nome, ordem)
VALUES ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001', 'Prefeito', 1),
       ('00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000000001', 'Vereador', 2);

INSERT INTO candidatos_publicos (id, cargo_id, nome_urna, nome_completo, numero, partido_sigla, partido_nome, fonte_nome, fonte_url, atualizado_em)
VALUES ('00000000-0000-4000-8000-000000001001', '00000000-0000-4000-8000-000000000101',
        'Ana Exemplo', 'Ana Exemplo da Silva (candidata fictícia)', '91', 'PEX', 'Partido Exemplo (fictício)',
        'TSE — DivulgaCandContas (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000001002', '00000000-0000-4000-8000-000000000101',
        'Bruno Modelo', 'Bruno Modelo Souza (candidato fictício)', '92', 'PMO', 'Partido Modelo (fictício)',
        'TSE — DivulgaCandContas (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000001003', '00000000-0000-4000-8000-000000000102',
        'Carla Teste', 'Carla Teste Oliveira (candidata fictícia)', '91123', 'PEX', 'Partido Exemplo (fictício)',
        'TSE — DivulgaCandContas (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z');

INSERT INTO cargos_anteriores_candidato (id, candidato_id, cargo, ano_inicio, ano_fim, fonte_nome, fonte_url, atualizado_em)
VALUES ('00000000-0000-4000-8000-000000002001', '00000000-0000-4000-8000-000000001001',
        'Vereadora de Cidade Exemplo', 2021, 2024,
        'TSE — Resultados de eleições anteriores (dado de exemplo)', 'https://resultados.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000002002', '00000000-0000-4000-8000-000000001001',
        'Secretária Municipal de Educação de Cidade Exemplo', 2025, NULL,
        'Diário Oficial de Cidade Exemplo (dado de exemplo)', NULL,
        '2026-10-20T12:00:00Z');

INSERT INTO propostas_candidato (id, candidato_id, tema, titulo, descricao, fonte_nome, fonte_url, atualizado_em)
VALUES ('00000000-0000-4000-8000-000000003001', '00000000-0000-4000-8000-000000001001', 'Educação',
        'Creche em tempo integral', 'Ampliar as vagas de creche em tempo integral nos bairros com maior fila de espera (proposta fictícia).',
        'Plano de governo registrado no TSE (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000003002', '00000000-0000-4000-8000-000000001001', 'Educação',
        'Reforma de escolas municipais', 'Reformar as escolas municipais com laudo de risco estrutural nos dois primeiros anos (proposta fictícia).',
        'Plano de governo registrado no TSE (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000003003', '00000000-0000-4000-8000-000000001001', 'Mobilidade',
        'Tarifa zero aos domingos', 'Gratuidade no transporte coletivo municipal aos domingos e feriados (proposta fictícia).',
        'Plano de governo registrado no TSE (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z'),
       ('00000000-0000-4000-8000-000000003004', '00000000-0000-4000-8000-000000001002', 'Saúde',
        'Unidade de saúde aberta à noite', 'Manter ao menos uma unidade básica de saúde aberta até as 22h em cada região (proposta fictícia).',
        'Plano de governo registrado no TSE (dado de exemplo)', 'https://divulgacandcontas.tse.jus.br/',
        '2026-11-01T12:00:00Z');

INSERT INTO noticias_candidato (id, candidato_id, titulo, resumo, publicada_em, fonte_nome, fonte_url, atualizado_em)
VALUES ('00000000-0000-4000-8000-000000004001', '00000000-0000-4000-8000-000000001001',
        'Candidatos à prefeitura debatem educação (notícia fictícia)',
        'Resumo de exemplo: no debate, Ana Exemplo defendeu a ampliação de creches e Bruno Modelo priorizou a saúde.',
        '2026-10-15', 'Jornal Exemplo (veículo fictício)', NULL,
        '2026-10-15T18:00:00Z');
