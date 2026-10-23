ALTER TABLE candidatos_chapa
ADD COLUMN data_nascimento DATE;

ALTER TABLE votacao
ADD COLUMN tipo_desempate VARCHAR(50) NOT NULL DEFAULT 'NENHUM';