#!/usr/bin/env bash
# Inicia o banco descartável usado pela validação de migrações no CI.
set -euo pipefail

# Services iniciam antes dos steps; usamos docker run para gerar
# a credencial neste job, sem depender de secrets em PRs de forks.
DB_PASSWORD="$(openssl rand -hex 32)"
echo "::add-mask::$DB_PASSWORD"
echo "DB_PASSWORD=$DB_PASSWORD" >> "$GITHUB_ENV"
export POSTGRES_PASSWORD="$DB_PASSWORD"
docker run -d --name migracoes-postgres \
  -e POSTGRES_USER="$DB_USERNAME" -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_PASSWORD -p 127.0.0.1:5432:5432 \
  --health-cmd "pg_isready -U eleicao -d eleicao" \
  --health-interval 5s --health-timeout 5s --health-retries 10 \
  postgres:17-alpine
for _ in $(seq 1 60); do
  if [ "$(docker inspect --format '{{.State.Health.Status}}' migracoes-postgres)" = healthy ]; then
    exit 0
  fi
  sleep 2
done
echo "::error::Timeout esperando PostgreSQL."
docker logs migracoes-postgres
exit 1
