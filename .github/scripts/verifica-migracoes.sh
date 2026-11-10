#!/usr/bin/env bash
# Verifica a imutabilidade da cadeia Flyway (RNF-08).
#
# Uso: .github/scripts/verifica-migracoes.sh <ref-base>   (ex.: origin/develop)
#
# Falha se:
#   1. alguma migração que já existe em <ref-base> foi modificada, renomeada ou
#      removida neste branch (só adição, status "A", é permitida). Migração
#      aplicada tem checksum gravado em flyway_schema_history; editá-la quebra o
#      start de qualquer ambiente que já a aplicou. Correção vira nova versão.
#   2. existe arquivo com nome fora do padrão V<n>__<descricao>.sql / R__<descricao>.sql;
#   3. duas migrações versionadas usam a mesma versão (ex.: V14__a.sql e V14__b.sql).
set -euo pipefail

BASE_REF="${1:?informe a ref base, ex.: origin/develop}"
DIR="src/main/resources/db/migration"
falhou=0

echo "==> Comparando $DIR com $BASE_REF"
# merge-base (...) para considerar só o que este branch mudou, não o que a base andou.
alteracoes="$(git diff --name-status "$BASE_REF"...HEAD -- "$DIR")"
if [[ -n "$alteracoes" ]]; then
    echo "$alteracoes"
fi
proibidas="$(printf '%s\n' "$alteracoes" | awk -F'\t' 'NF && $1 != "A"')"
if [[ -n "$proibidas" ]]; then
    echo "::error::Migrações existentes foram modificadas, renomeadas ou removidas. Crie uma nova versão em vez de editar:"
    printf '%s\n' "$proibidas"
    falhou=1
fi

echo "==> Validando nomes e versões duplicadas"
nomes_invalidos="$(find "$DIR" -type f -printf '%f\n' | grep -Ev '^(V[0-9]+__[a-z0-9_]+|R__[a-z0-9_]+)\.sql$' || true)"
if [[ -n "$nomes_invalidos" ]]; then
    echo "::error::Arquivos fora do padrão V<n>__<descricao_snake_case>.sql:"
    printf '%s\n' "$nomes_invalidos"
    falhou=1
fi

duplicadas="$(find "$DIR" -type f -name 'V*__*.sql' -printf '%f\n' \
    | sed -E 's/^V([0-9]+)__.*/\1/' | sed -E 's/^0+([0-9])/\1/' | sort -n | uniq -d)"
if [[ -n "$duplicadas" ]]; then
    echo "::error::Versões de migração duplicadas: $(echo "$duplicadas" | tr '\n' ' ')"
    falhou=1
fi

if [[ "$falhou" -ne 0 ]]; then
    exit 1
fi
echo "OK: cadeia de migrações íntegra."
