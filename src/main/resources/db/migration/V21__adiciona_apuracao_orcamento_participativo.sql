ALTER TABLE apuracoes ALTER COLUMN regra DROP NOT NULL;
ALTER TABLE apuracoes ALTER COLUMN resultado DROP NOT NULL;
ALTER TABLE apuracoes ADD COLUMN resultado_orcamento JSONB;
ALTER TABLE apuracoes ADD CONSTRAINT chk_apuracoes_resultado_presente
    CHECK ((resultado IS NOT NULL AND resultado_orcamento IS NULL)
        OR (resultado IS NULL AND resultado_orcamento IS NOT NULL));
