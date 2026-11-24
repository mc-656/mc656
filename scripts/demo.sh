#!/usr/bin/env bash
# ==============================================================================
# Script de carga para demonstração local — Decisão Coletiva (MC656)
# ==============================================================================
# Pré-requisitos:
#   1. Postgres + Mailpit no ar:
#        docker compose up -d postgres mailpit
#   2. Aplicação rodando com agendador acelerado (5s) para abrir/apurar rápido:
#        ./mvnw spring-boot:run -Dspring-boot.run.arguments="--votacao.agendador.intervalo=PT5S"
#   3. Dependências locais: curl, jq, python3
#
# Uso:
#   make demo
#   # ou:
#   bash scripts/demo.sh
# ==============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
SENHA_PADRAO="${DEMO_SENHA:-Senha123!}"

VERDE='\033[0;32m'
AZUL='\033[0;34m'
AMARELO='\033[1;33m'
VERMELHO='\033[0;31m'
NC='\033[0m'

info()    { echo -e "${AZUL}[INFO]${NC} $*"; }
ok()      { echo -e "${VERDE}[OK]${NC} $*"; }
aviso()   { echo -e "${AMARELO}[AVISO]${NC} $*"; }
erro()    { echo -e "${VERMELHO}[ERRO]${NC} $*" >&2; exit 1; }

# ------------------------------------------------------------------------------
# 0. Verificações iniciais
# ------------------------------------------------------------------------------
for cmd in curl jq python3; do
  command -v "$cmd" >/dev/null 2>&1 || erro "Comando obrigatório não encontrado: $cmd"
done

info "Verificando se a aplicação está de pé em $BASE_URL..."
curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1 \
  || erro "Aplicação não respondeu em $BASE_URL/actuator/health. Suba o Spring Boot antes!"
ok "Aplicação respondendo!"

if curl -sf "$MAILPIT_URL/api/v1/messages" >/dev/null 2>&1; then
  ok "Mailpit respondendo em $MAILPIT_URL!"
else
  aviso "Mailpit não respondeu em $MAILPIT_URL. O envio de convites por e-mail pode falhar se não estiver no ar."
fi

iso_offset() {
  python3 -c "from datetime import datetime, timezone, timedelta; print((datetime.now(timezone.utc) + timedelta(seconds=$1)).strftime('%Y-%m-%dT%H:%M:%SZ'))"
}

cpf_deterministico() {
  python3 - "$1" <<'PY'
import hashlib, sys
h = hashlib.sha256(sys.argv[1].encode()).hexdigest()
n = [int(h[i], 16) % 10 for i in range(9)]
if len(set(n)) == 1:
    n[0] = (n[0] + 1) % 10
d1 = sum(v * (10 - i) for i, v in enumerate(n)) % 11
n.append(0 if d1 < 2 else 11 - d1)
d2 = sum(v * (11 - i) for i, v in enumerate(n)) % 11
n.append(0 if d2 < 2 else 11 - d2)
print("".join(map(str, n)))
PY
}

cadastrar_e_logar() {
  local nome="$1" email="$2"
  local cpf login_res cad_res token
  cpf=$(cpf_deterministico "$email")

  # Se a conta já existir com a senha padrão, reaproveita direto
  if login_res=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$email\",\"senha\":\"$SENHA_PADRAO\"}" 2>/dev/null); then
    token=$(echo "$login_res" | jq -r '.token')
    if [[ -n "$token" && "$token" != "null" ]]; then
      echo "$token"
      return 0
    fi
  fi

  cad_res=$(curl -s -X POST "$BASE_URL/api/usuarios" \
    -H "Content-Type: application/json" \
    -d "{\"nome\":\"$nome\",\"cpf\":\"$cpf\",\"email\":\"$email\",\"senha\":\"$SENHA_PADRAO\",\"dataNascimento\":\"1995-05-15\"}")

  login_res=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"senha\":\"$SENHA_PADRAO\"}" || true)
  token=$(echo "$login_res" | jq -r '.token // empty')
  [[ -n "$token" ]] || erro "Falha ao cadastrar/autenticar $email (resposta cadastro: $cad_res)"
  echo "$token"
}

aguardar_estado() {
  local votacao_id="$1" estado_alvo="$2" token="$3" max_tentativas="${4:-30}"
  for ((i=1; i<=max_tentativas; i++)); do
    local ultimo
    ultimo=$(curl -sf "$BASE_URL/api/votacoes/$votacao_id/transicoes" \
      -H "Authorization: Bearer $token" | jq -r '.[-1].estadoNovo // empty')
    if [[ "$ultimo" == "$estado_alvo" ]]; then
      return 0
    fi
    sleep 2
  done
  aviso "Votação $votacao_id não atingiu '$estado_alvo' no tempo esperado (verifique se --votacao.agendador.intervalo=PT5S está ativo)."
  return 1
}

# ------------------------------------------------------------------------------
# 1. Criar contas de demonstração
# ------------------------------------------------------------------------------
info "Criando/autenticando contas de demonstração..."
ADMIN_TOKEN=$(cadastrar_e_logar "Ana Organizadora" "admin@demo.unicamp.br")
ELEITOR1_TOKEN=$(cadastrar_e_logar "Bruno Eleitor" "eleitor1@demo.unicamp.br")
ELEITOR2_TOKEN=$(cadastrar_e_logar "Carla Eleitora" "eleitor2@demo.unicamp.br")
ELEITOR3_TOKEN=$(cadastrar_e_logar "Diego Convidado" "eleitor3@demo.unicamp.br")
ok "4 contas prontas (senha padrão: $SENHA_PADRAO)"

# ------------------------------------------------------------------------------
# 2. CENÁRIO A — Eleição Privada EM VOTAÇÃO (comparecimento + convite Mailpit + voto ao vivo)
# ------------------------------------------------------------------------------
info "Criando Cenário A: Eleição do Centro Acadêmico 2026 (EmVotacao)..."
INICIO_A=$(iso_offset 4)
FIM_A=$(iso_offset 86400)

VOTACAO_A=$(curl -sf -X POST "$BASE_URL/api/votacoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tipo\":\"ELEICAO_PRIVADA\",\"nome\":\"Eleição do Centro Acadêmico 2026\",\"descricao\":\"Escolha da diretoria executiva do CA para a gestão 2026/2027.\",\"inicioEm\":\"$INICIO_A\",\"fimEm\":\"$FIM_A\"}" \
  | jq -r '.id')

curl -sf -X PUT "$BASE_URL/api/votacoes/$VOTACAO_A/regras" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tipoMaioria":"MAIORIA_SIMPLES","segundoTurno":false,"quorumMinimoPercentual":50,"votoPonderado":false}' >/dev/null

CHAPA_A1=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/candidatos" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Chapa 1 — Renovação","descricao":"Foco em infraestrutura, laboratórios 24h e permanência estudantil."}' | jq -r '.id')

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/candidatos" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Chapa 2 — Integração","descricao":"Integração entre institutos, semana acadêmica e extensão."}' >/dev/null

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/candidatos" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Chapa 3 — Transparência","descricao":"Gestão aberta, prestação de contas mensal e assembleias digitais."}' >/dev/null

for email in "eleitor1@demo.unicamp.br" "eleitor2@demo.unicamp.br"; do
  curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/eleitores" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\"}" >/dev/null
done

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/transicoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"destino":"Publicada"}' >/dev/null

CONVITE_LINK=$(curl -sf -X PUT "$BASE_URL/api/votacoes/$VOTACAO_A/convite" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"modo":"ABERTO"}' | jq -r '.link')

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/convite/emails" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"emails":["eleitor3@demo.unicamp.br"]}' >/dev/null || aviso "Não foi possível enviar convite por e-mail (Mailpit está rodando?)"

ok "Cenário A publicado e convite enviado para eleitor3@demo.unicamp.br!"

# ------------------------------------------------------------------------------
# 3. CENÁRIO B — Eleição Privada APURADA (pronta para Publicar e Homologar)
# ------------------------------------------------------------------------------
info "Criando Cenário B: Consulta do Conselho do Instituto 2026 (será apurada automaticamente)..."
INICIO_B=$(iso_offset 3)
FIM_B=$(iso_offset 14)

VOTACAO_B=$(curl -sf -X POST "$BASE_URL/api/votacoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tipo\":\"ELEICAO_PRIVADA\",\"nome\":\"Consulta do Conselho do Instituto 2026\",\"descricao\":\"Escolha da representação docente/discente no conselho.\",\"inicioEm\":\"$INICIO_B\",\"fimEm\":\"$FIM_B\"}" \
  | jq -r '.id')

curl -sf -X PUT "$BASE_URL/api/votacoes/$VOTACAO_B/regras" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tipoMaioria":"MAIORIA_SIMPLES","segundoTurno":false,"quorumMinimoPercentual":50,"votoPonderado":false}' >/dev/null

CHAPA_B1=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/candidatos" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Proposta A — Novo Currículo Modular","descricao":"Atualização das trilhas eletivas de Engenharia de Software."}' | jq -r '.id')

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/candidatos" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Proposta B — Manutenção do Currículo Atual","descricao":"Preservação da grade vigente com ajustes pontuais."}' >/dev/null

for email in "eleitor1@demo.unicamp.br" "eleitor2@demo.unicamp.br"; do
  curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/eleitores" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\"}" >/dev/null
done

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/transicoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"destino":"Publicada"}' >/dev/null

# ------------------------------------------------------------------------------
# 4. CENÁRIO C — Orçamento Participativo EM VOTAÇÃO (distribuição de pontos ao vivo)
# ------------------------------------------------------------------------------
info "Criando Cenário C: Orçamento Participativo do Campus 2026 (EmVotacao)..."
INICIO_C=$(iso_offset 4)
FIM_C=$(iso_offset 86400)

VOTACAO_C=$(curl -sf -X POST "$BASE_URL/api/votacoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tipo\":\"ORCAMENTO_PARTICIPATIVO\",\"nome\":\"Orçamento Participativo do Campus 2026\",\"descricao\":\"Distribua seus pontos entre os projetos prioritários para o campus.\",\"inicioEm\":\"$INICIO_C\",\"fimEm\":\"$FIM_C\"}" \
  | jq -r '.id')

curl -sf -X PUT "$BASE_URL/api/votacoes/$VOTACAO_C/regras" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tipoMaioria":"MAIORIA_SIMPLES","segundoTurno":false,"quorumMinimoPercentual":50,"votoPonderado":false}' >/dev/null

curl -sf -X PUT "$BASE_URL/api/votacoes/$VOTACAO_C/orcamento" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orcamentoTotal":500000.00}' >/dev/null

PROP_C1=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/propostas" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Iluminação LED nas trilhas","descricao":"Instalação de postes LED solares nos caminhos entre institutos.","custo":200000.00}' | jq -r '.proposta.id')

PROP_C2=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/propostas" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Reforma das salas de estudo","descricao":"Climatização, tomadas e mobiliário ergonômico na biblioteca.","custo":250000.00}' | jq -r '.proposta.id')

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/propostas" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Bicicletários cobertos","descricao":"Novos bicicletários com monitoramento perto do RU e do CB.","custo":150000.00}' >/dev/null

PROP_C4=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/propostas" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Bebedouros com água gelada","descricao":"Substituição e instalação de 15 bebedouros acessíveis.","custo":100000.00}' | jq -r '.proposta.id')

for email in "eleitor1@demo.unicamp.br" "eleitor2@demo.unicamp.br"; do
  curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/eleitores" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\"}" >/dev/null
done

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/transicoes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"destino":"Publicada"}' >/dev/null

# ------------------------------------------------------------------------------
# 5. Aguardar abertura automática e registrar votos iniciais
# ------------------------------------------------------------------------------
info "Aguardando agendador abrir as votações (EmVotacao)..."
aguardar_estado "$VOTACAO_B" "EmVotacao" "$ADMIN_TOKEN" 25
aguardar_estado "$VOTACAO_A" "EmVotacao" "$ADMIN_TOKEN" 25
aguardar_estado "$VOTACAO_C" "EmVotacao" "$ADMIN_TOKEN" 25

info "Registrando votos na Eleição B (para apuração)..."
COMPROVANTE_B=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/votos" \
  -H "Authorization: Bearer $ELEITOR1_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"opcaoId\":\"$CHAPA_B1\"}" | jq -r '.comprovante')

curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_B/votos" \
  -H "Authorization: Bearer $ELEITOR2_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"opcaoId\":\"$CHAPA_B1\"}" >/dev/null

info "Registrando voto inicial na Eleição A (50% comparecimento)..."
COMPROVANTE_A=$(curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_A/votos" \
  -H "Authorization: Bearer $ELEITOR1_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"opcaoId\":\"$CHAPA_A1\"}" | jq -r '.comprovante')

info "Registrando voto inicial no Orçamento Participativo C..."
curl -sf -X POST "$BASE_URL/api/votacoes/$VOTACAO_C/votos-orcamento" \
  -H "Authorization: Bearer $ELEITOR1_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"alocacoes\":[{\"propostaId\":\"$PROP_C1\",\"pontos\":200000},{\"propostaId\":\"$PROP_C2\",\"pontos\":200000},{\"propostaId\":\"$PROP_C4\",\"pontos\":100000}]}" >/dev/null

info "Aguardando encerramento e apuração automática do Cenário B..."
aguardar_estado "$VOTACAO_B" "Apuracao" "$ADMIN_TOKEN" 25

# ------------------------------------------------------------------------------
# 6. Resumo pronto para uso
# ------------------------------------------------------------------------------
echo ""
echo -e "${VERDE}==============================================================================${NC}"
echo -e "${VERDE}  AMBIENTE DE DEMONSTRAÇÃO PRONTO!${NC}"
echo -e "${VERDE}==============================================================================${NC}"
echo ""
echo -e "${AZUL}CREDENCIAIS (senha para todas: ${SENHA_PADRAO})${NC}"
echo "  Admin (organizadora):  admin@demo.unicamp.br"
echo "  Eleitor 1 (já votou):  eleitor1@demo.unicamp.br"
echo "  Eleitor 2 (p/ votar!): eleitor2@demo.unicamp.br"
echo "  Eleitor 3 (convite):   eleitor3@demo.unicamp.br"
echo ""
echo -e "${AZUL}LINKS DIRETOS${NC}"
echo "  1. Eleições Públicas (M4, sem login):  $BASE_URL/eleicoes-publicas"
echo "  2. Painel Cenário A (Comparecimento):  $BASE_URL/votacoes/$VOTACAO_A"
echo "  3. Votar ao vivo Cenário A (eleitor2): $BASE_URL/votacoes/$VOTACAO_A/votar"
echo "  4. Mailpit (convite p/ eleitor3):      $MAILPIT_URL"
echo "  5. Link direto do convite (RF-17):     $CONVITE_LINK"
echo "  6. Resultado Cenário B (Publicar/Hom.):$BASE_URL/votacoes/$VOTACAO_B/resultado"
echo "  7. Verificar comprovante:              $BASE_URL/comprovantes/verificar"
echo "  8. Votar ao vivo OP Cenário C:         $BASE_URL/votacoes/$VOTACAO_C/votar"
echo "  9. Swagger UI (API REST):              $BASE_URL/swagger-ui.html"
echo ""
echo -e "${AZUL}COMPROVANTES CRIPTOGRAFADOS (colar em /comprovantes/verificar)${NC}"
echo "  Cenário B (já APURADO — inclusão confirmada):"
echo "    $COMPROVANTE_B"
echo ""
echo "  Cenário A (ainda EM VOTAÇÃO — registrado, aguardando apuração):"
echo "    $COMPROVANTE_A"
echo ""
