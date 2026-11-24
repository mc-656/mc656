ALTER TABLE eleicoes_publicas ADD COLUMN data_fim DATE;

CREATE TABLE aparicoes_candidato (
    id UUID PRIMARY KEY,
    candidato_id UUID NOT NULL REFERENCES candidatos_publicos(id) ON DELETE CASCADE,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    data_hora TIMESTAMPTZ NOT NULL,
    local VARCHAR(200) NOT NULL,
    descricao TEXT,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_aparicoes_candidato_data ON aparicoes_candidato (candidato_id, data_hora);

CREATE TABLE conteudos_publicos (
    id UUID PRIMARY KEY,
    eleicao_id UUID NOT NULL REFERENCES eleicoes_publicas(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(250) NOT NULL,
    tema VARCHAR(60),
    resumo TEXT NOT NULL,
    publicada_em DATE NOT NULL,
    fonte_nome VARCHAR(150) NOT NULL,
    fonte_url VARCHAR(500),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_conteudos_publicos_eleicao_data ON conteudos_publicos (eleicao_id, publicada_em DESC);

CREATE TABLE conteudos_publicos_candidatos (
    conteudo_id UUID NOT NULL REFERENCES conteudos_publicos(id) ON DELETE CASCADE,
    candidato_id UUID NOT NULL REFERENCES candidatos_publicos(id) ON DELETE CASCADE,
    PRIMARY KEY (conteudo_id, candidato_id)
);

CREATE TABLE topicos_debate (
    id UUID PRIMARY KEY,
    conteudo_id UUID NOT NULL REFERENCES conteudos_publicos(id) ON DELETE CASCADE,
    tema VARCHAR(60) NOT NULL,
    ordem INT NOT NULL,
    resumo TEXT NOT NULL
);

CREATE INDEX idx_topicos_debate_conteudo_tema ON topicos_debate (conteudo_id, tema, ordem);

-- Agenda futura para a candidata já existente em V15 (sem alterar histórico, propostas nem notícias de V15).
INSERT INTO aparicoes_candidato (
    id, candidato_id, tipo, titulo, data_hora, local, descricao, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000005001',
    '00000000-0000-4000-8000-000000001001',
    'DEBATE',
    'Debate sobre educação e mobilidade urbana',
    '2028-09-15T20:00:00Z',
    'Auditório Central de Exemplo',
    'Encontro aberto entre candidatas e candidatos à prefeitura.',
    'TSE · Agenda Pública',
    'https://www.tse.jus.br/',
    '2026-10-01T12:00:00Z'
);

-- Eleição ATIVA (2026-01-01 a 2027-12-31) com múltiplos candidatos para o comparador (RF-31) e notícias/debates (RF-33).
INSERT INTO eleicoes_publicas (
    id, nome, tipo, ano, data_primeiro_turno, data_fim, abrangencia, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000000002',
    'Eleições Gerais Modelo 2026',
    'Estadual',
    2026,
    '2026-01-01',
    '2027-12-31',
    'Estado de São Paulo',
    'TSE · Dados Abertos',
    'https://dadosabertos.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000000003',
    'Eleição Municipal 2022',
    'Municipal',
    2022,
    '2022-10-02',
    '2022-10-30',
    'Campinas/SP',
    'TSE · Histórico',
    'https://dadosabertos.tse.jus.br/',
    '2022-11-01T12:00:00Z'
);

INSERT INTO cargos_publicos (id, eleicao_id, nome, ordem)
VALUES ('00000000-0000-4000-8000-000000000201', '00000000-0000-4000-8000-000000000002', 'Governador', 1),
       ('00000000-0000-4000-8000-000000000202', '00000000-0000-4000-8000-000000000002', 'Senador', 2),
       ('00000000-0000-4000-8000-000000000301', '00000000-0000-4000-8000-000000000003', 'Prefeito', 1);

INSERT INTO candidatos_publicos (
    id, cargo_id, nome_urna, nome_completo, numero, partido_sigla, partido_nome, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000001004',
    '00000000-0000-4000-8000-000000000201',
    'Marina Serra',
    'Marina Almeida Serra',
    '45',
    'PSB-EX',
    'Partido Social Brasileiro Exemplo',
    'TSE · DivulgaCand',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000001005',
    '00000000-0000-4000-8000-000000000201',
    'Lucas Prado',
    'Lucas Henrique Prado',
    '13',
    'PT-EX',
    'Partido Trabalhista Exemplo',
    'TSE · DivulgaCand',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000001006',
    '00000000-0000-4000-8000-000000000201',
    'Sofia Mendes',
    'Sofia Costa Mendes',
    '15',
    'MDB-EX',
    'Movimento Democrático Exemplo',
    'TSE · DivulgaCand',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000001007',
    '00000000-0000-4000-8000-000000000202',
    'Rafael Costa',
    'Rafael Oliveira Costa',
    '400',
    'PSB-EX',
    'Partido Social Brasileiro Exemplo',
    'TSE · DivulgaCand',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000001008',
    '00000000-0000-4000-8000-000000000301',
    'Roberto Dias',
    'Roberto Silva Dias',
    '50',
    'PEX',
    'Partido Exemplo Histórico',
    'TSE · Histórico',
    'https://divulgacandcontas.tse.jus.br/',
    '2022-11-01T12:00:00Z'
);

INSERT INTO propostas_candidato (
    id, candidato_id, tema, titulo, descricao, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000003005',
    '00000000-0000-4000-8000-000000001004',
    'Educação',
    'Ensino técnico integrado no estado',
    'Expandir vagas de ensino técnico integrado ao médio em todas as regiões.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000003006',
    '00000000-0000-4000-8000-000000001004',
    'Saúde',
    'Prontuário unificado estadual',
    'Integrar prontuários entre UBS municipais e hospitais estaduais.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000003007',
    '00000000-0000-4000-8000-000000001005',
    'Educação',
    'Valorização salarial do magistério',
    'Recomposição do piso docente e formação continuada em rede pública.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000003008',
    '00000000-0000-4000-8000-000000001005',
    'Saúde',
    'Mutirões regionais de exames',
    'Reduzir filas de exames de imagem com unidades móveis.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000003009',
    '00000000-0000-4000-8000-000000001006',
    'Educação',
    'Escolas em tempo integral sustentável',
    'Reforma energética e ampliação do turno integral.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000003010',
    '00000000-0000-4000-8000-000000001006',
    'Mobilidade',
    'Trens regionais e corredores elétricos',
    'Conectar polos metropolitanos com bilhete único estadual.',
    'Plano de Governo · TSE',
    'https://divulgacandcontas.tse.jus.br/',
    '2026-10-01T12:00:00Z'
);

INSERT INTO aparicoes_candidato (
    id, candidato_id, tipo, titulo, data_hora, local, descricao, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000005002',
    '00000000-0000-4000-8000-000000001004',
    'SABATINA',
    'Sabatina sobre financiamento da educação',
    '2027-08-20T19:30:00Z',
    'Teatro Estadual',
    'Apresentação de propostas para o ensino médio técnico.',
    'Agência Pública Exemplo',
    'https://www.tse.jus.br/',
    '2026-10-01T12:00:00Z'
),
(
    '00000000-0000-4000-8000-000000005003',
    '00000000-0000-4000-8000-000000001004',
    'ENCONTRO_PASSADO',
    'Evento histórico encerrado',
    '2024-01-10T10:00:00Z',
    'Centro Antigo',
    'Aparição passada usada para garantir filtragem de agenda futura.',
    'Arquivo Público',
    NULL,
    '2026-10-01T12:00:00Z'
);

INSERT INTO conteudos_publicos (
    id, eleicao_id, tipo, titulo, tema, resumo, publicada_em, fonte_nome, fonte_url, atualizado_em
)
VALUES (
    '00000000-0000-4000-8000-000000006001',
    '00000000-0000-4000-8000-000000000002',
    'RESUMO_DEBATE',
    'Primeiro Debate Estadual — Educação e Saúde',
    'Educação',
    'Síntese temática das posições apresentadas pelos candidatos ao governo estadual.',
    '2026-10-15',
    'TV Universitária · Cobertura Oficial',
    'https://www.tse.jus.br/',
    '2026-10-16T10:00:00Z'
),
(
    '00000000-0000-4000-8000-000000006002',
    '00000000-0000-4000-8000-000000000002',
    'NOTICIA',
    'Marina Serra apresenta plano de ensino técnico integrado',
    'Educação',
    'Proposta prevê expansão de vagas em parceria com institutos estaduais.',
    '2026-10-18',
    'Jornal Regional Exemplo',
    'https://dadosabertos.tse.jus.br/',
    '2026-10-18T14:00:00Z'
),
(
    '00000000-0000-4000-8000-000000006003',
    '00000000-0000-4000-8000-000000000002',
    'NOTICIA',
    'Lucas Prado detalha mutirões de diagnóstico médico',
    'Saúde',
    'Campanha destacou redução do tempo de espera por exames especializados.',
    '2026-10-20',
    'Portal Cívico Estadual',
    'https://dadosabertos.tse.jus.br/',
    '2026-10-20T16:00:00Z'
);

INSERT INTO conteudos_publicos_candidatos (conteudo_id, candidato_id)
VALUES ('00000000-0000-4000-8000-000000006001', '00000000-0000-4000-8000-000000001004'),
       ('00000000-0000-4000-8000-000000006001', '00000000-0000-4000-8000-000000001005'),
       ('00000000-0000-4000-8000-000000006001', '00000000-0000-4000-8000-000000001006'),
       ('00000000-0000-4000-8000-000000006002', '00000000-0000-4000-8000-000000001004'),
       ('00000000-0000-4000-8000-000000006003', '00000000-0000-4000-8000-000000001005');

INSERT INTO topicos_debate (id, conteudo_id, tema, ordem, resumo)
VALUES (
    '00000000-0000-4000-8000-000000007001',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    1,
    'Ampliação do ensino técnico integrado ao médio em regiões periféricas.'
),
(
    '00000000-0000-4000-8000-000000007002',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    2,
    'Valorização da carreira docente e metas de alfabetização na idade certa.'
),
(
    '00000000-0000-4000-8000-000000007003',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    3,
    'Reforma energética e conectividade de laboratórios escolares.'
),
(
    '00000000-0000-4000-8000-000000007004',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    4,
    'Parcerias com universidades públicas para formação continuada.'
),
(
    '00000000-0000-4000-8000-000000007005',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    5,
    'Transparência nos repasses da merenda e transporte escolar.'
),
(
    '00000000-0000-4000-8000-000000007006',
    '00000000-0000-4000-8000-000000006001',
    'Educação',
    6,
    'Sexto tópico omitido na exibição resumida para preservar o limite de 5 por tema.'
),
(
    '00000000-0000-4000-8000-000000007007',
    '00000000-0000-4000-8000-000000006001',
    'Saúde',
    1,
    'Integração digital de prontuários entre atenção básica e hospitais.'
),
(
    '00000000-0000-4000-8000-000000007008',
    '00000000-0000-4000-8000-000000006001',
    'Saúde',
    2,
    'Redução das filas para exames e cirurgias eletivas via mutirões regionais.'
);
