-- RF-21/RF-22: Voto em orçamento participativo com distribuição de pontos entre propostas.
-- Segue o mesmo princípio de sigilo do voto (RNF-02) e imutabilidade append-only (SPECS §7.3).
-- A participação do usuário fica em 'participacoes' (V16) para garantir voto único.
-- A cédula e suas alocações de pontos ficam sem vínculo com o usuário.

CREATE TABLE cedulas_orcamento (
    id UUID PRIMARY KEY,
    votacao_id UUID NOT NULL REFERENCES votacoes (id)
);

CREATE INDEX cedulas_orcamento_votacao ON cedulas_orcamento (votacao_id);

CREATE TABLE alocacoes_orcamento (
    id UUID PRIMARY KEY,
    cedula_id UUID NOT NULL REFERENCES cedulas_orcamento (id),
    proposta_id UUID NOT NULL REFERENCES propostas (id),
    pontos NUMERIC(15, 2) NOT NULL,
    CONSTRAINT alocacoes_pontos_nao_negativos CHECK (pontos >= 0)
);

CREATE INDEX alocacoes_orcamento_cedula ON alocacoes_orcamento (cedula_id);
CREATE INDEX alocacoes_orcamento_proposta ON alocacoes_orcamento (proposta_id);

-- Append-only: reusa a função recusa_alteracao_de_voto criada na V16
CREATE TRIGGER cedulas_orcamento_append_only
    BEFORE UPDATE OR DELETE ON cedulas_orcamento
    FOR EACH ROW EXECUTE FUNCTION recusa_alteracao_de_voto();

CREATE TRIGGER alocacoes_orcamento_append_only
    BEFORE UPDATE OR DELETE ON alocacoes_orcamento
    FOR EACH ROW EXECUTE FUNCTION recusa_alteracao_de_voto();
