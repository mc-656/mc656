#!/usr/bin/env sh
# Troca a versão da API na VPS (chamado pelo job deploy do cd.yml via SSH, ou à mão).
# Uso: ./deploy.sh ghcr.io/<owner>/<repo>:sha-<commit>
# Grava a imagem no .env e sobe esperando o HEALTHCHECK ficar healthy. Se não ficar,
# volta para a imagem anterior e sai com erro, para a aplicação não ficar fora do ar.
set -eu

imagem="${1:?uso: deploy.sh <imagem>}"
cd "$(dirname "$0")"
if [ ! -f .env ]; then
    echo "Falta $(pwd)/.env (copie de .env.example do repositório e preencha)." >&2
    exit 1
fi

anterior=$(sed -n 's/^IMAGEM=//p' .env)

define_imagem() {
    if grep -q '^IMAGEM=' .env; then
        sed -i "s|^IMAGEM=.*|IMAGEM=$1|" .env
    else
        printf '\nIMAGEM=%s\n' "$1" >> .env
    fi
}

define_imagem "$imagem"
docker compose pull app
if ! docker compose up -d --wait --wait-timeout 300; then
    echo "Versão $imagem não ficou saudável." >&2
    docker compose logs --tail 100 app >&2 || true
    if [ -n "$anterior" ] && [ "$anterior" != "$imagem" ]; then
        echo "Voltando para $anterior." >&2
        define_imagem "$anterior"
        docker compose up -d --wait --wait-timeout 300 || true
    fi
    exit 1
fi
docker image prune -f > /dev/null
echo "No ar: $imagem"
