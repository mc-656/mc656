#!/usr/bin/env sh
# Troca a versão da API na VPS.
#   À mão:  ./deploy.sh ghcr.io/mc-656/mc656:sha-<commit>
#   CD:     comando forçado da chave de deploy no authorized_keys (README, "Deploy em VPS");
#           a imagem chega em SSH_ORIGINAL_COMMAND e, opcionalmente, um token do GHCR no stdin.
# A chave de deploy só consegue executar este script: quem tiver acesso aos secrets do
# GitHub não ganha shell na VPS, só consegue pedir o deploy de uma tag sha- desta imagem.
# Grava a imagem no .env e sobe esperando o HEALTHCHECK ficar healthy. Se não ficar,
# volta para a imagem anterior e sai com erro, para a aplicação não ficar fora do ar.
set -eu

IMAGEM_PERMITIDA='^ghcr\.io/mc-656/mc656:sha-[0-9a-f]{7,40}$'

imagem="${SSH_ORIGINAL_COMMAND:-${1:-}}"
# Valida antes de qualquer uso: o valor vem de fora e acaba no sed e no compose. O case barra
# quebra de linha e afins, que o grep (que testa linha a linha) deixaria passar.
case "$imagem" in
    '' | *[!a-z0-9.:/-]*) imagem_valida=false ;;
    *) imagem_valida=true ;;
esac
if [ "$imagem_valida" = false ] || ! printf '%s' "$imagem" | grep -Eq "$IMAGEM_PERMITIDA"; then
    echo "Imagem recusada: '$imagem' (esperado ghcr.io/mc-656/mc656:sha-<commit>)." >&2
    exit 2
fi

cd "$(dirname "$0")"
if [ ! -f .env ]; then
    echo "Falta $(pwd)/.env (copie de .env.example do repositório e preencha)." >&2
    exit 1
fi

# Pacote privado no GHCR: o CD manda o GITHUB_TOKEN do job (expira com ele) no stdin.
token=""
if [ -n "${SSH_ORIGINAL_COMMAND:-}" ] && [ ! -t 0 ]; then
    token=$(head -n 1 || true)
fi
if [ -n "$token" ]; then
    printf '%s' "$token" | docker login ghcr.io -u deploy --password-stdin > /dev/null
    trap 'docker logout ghcr.io > /dev/null 2>&1 || true' EXIT
fi

anterior=$(sed -n 's/^IMAGEM=//p' .env)

# Reescreve o .env no próprio arquivo (sem sed -i, que cria um novo na pasta): o usuário de
# deploy só precisa de escrita no .env, não na pasta onde ficam compose.yaml e este script.
define_imagem() {
    if grep -q '^IMAGEM=' .env; then
        novo=$(sed "s|^IMAGEM=.*|IMAGEM=$1|" .env)
        printf '%s\n' "$novo" > .env
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
