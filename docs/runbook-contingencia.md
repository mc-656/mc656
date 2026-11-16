# Runbook de contingência durante `EmVotacao` (RNF-06)

Uma falha enquanto a votação está em `EmVotacao` pode invalidar o processo (SPECS §6, §10). Este
documento diz o que monitorar, quando suspender e como suspender, retomar ou cancelar.

## 1. Onde olhar

| Endpoint | Acesso | Uso |
|---|---|---|
| `GET /actuator/health/liveness` | público | A JVM está viva. Se cair, reiniciar a instância. Não inclui o banco de propósito. |
| `GET /actuator/health/readiness` | público | Pronta para tráfego. Inclui o banco (`components.db`). `DOWN` = não registra voto. |
| `GET /actuator/health` | público | Status geral e de cada componente, sem detalhes de infraestrutura. |
| `GET /actuator/prometheus` | JWT de conta autorizada | Métricas para o Prometheus. Sem token: 401; conta fora da lista: 403. |

Só as contas cujo e-mail está em `METRICAS_EMAILS` (propriedade
`observabilidade.metricas.emails-autorizados`, separada por vírgula) leem as métricas; vazio nega a
todos. Crie uma conta de serviço via `POST /api/usuarios`, ponha o e-mail dela na variável e
reinicie a aplicação.

O token expira em `jwt.expiresIn` (120 min por padrão) e não há refresh: um job (cron) precisa
refazer o login e regravar o arquivo do token antes disso, senão o scrape passa a receber 401 e
os alertas param. Uma credencial estática de scrape é melhoria futura. Exemplo de coleta:

```yaml
scrape_configs:
  - job_name: eleicao
    metrics_path: /actuator/prometheus
    authorization:
      type: Bearer
      credentials_file: /etc/prometheus/eleicao.token
    static_configs:
      - targets: ["api:8080"]
```

### Logs

Com o profile `log-json` (`SPRING_PROFILES_ACTIVE=log-json`) cada linha sai em JSON (ECS) com o
campo `correlationId`. O mesmo id volta ao cliente no header `X-Correlation-Id`: peça esse valor a
quem relatar o erro e filtre o log por ele. Os logs nunca trazem a opção votada associada a um
eleitor (RNF-02); não acrescente isso ao investigar.

## 2. Métricas e sinais de alerta

| Métrica (PromQL) | O que indica | Alerta sugerido |
|---|---|---|
| `votacao_em_votacao` | Votações abertas agora. | Informativo: define se uma falha é crítica (> 0). |
| `rate(votacao_votos_registrados_total[5m]) * 60` | Votos por minuto. | Queda brusca a zero com `votacao_em_votacao > 0` em horário de pico. |
| `rate(votacao_votos_falhas_total[5m])` por `erro` | Falhas no registro de voto. | Qualquer `erro` diferente de regra de negócio esperada (ex.: voto duplicado) por mais de 5 min. |
| `rate(votacao_transicoes_falhas_total[5m])` por `destino`, `origem`, `erro` | Transições recusadas, com erro ou revertidas no commit (`erro="FalhaNoCommit"`). `origem="SISTEMA"` = job de abertura/encerramento; `origem="USUARIO"` = tentativa de admin (geralmente transição inválida, sem gravidade). | Qualquer falha com `origem="SISTEMA"`, ou `erro="FalhaNoCommit"`. |
| `increase(votacao_transicoes_total{destino="Suspensa"}[1h])` | Suspensões feitas. | Informativo, para o relatório pós-incidente. |
| `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | Taxa de erro 5xx. | > 2% por 5 min. |
| `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))` | Latência p95. | > 2 s por 5 min. Requer `management.metrics.distribution.percentiles-histogram.http.server.requests=true`; sem isso, use `http_server_requests_seconds_max`. |
| `hikaricp_connections_pending`, `hikaricp_connections_timeout_total` | Pool de conexões saturado. | `pending > 0` por 2 min ou qualquer timeout. |

> As métricas de voto (`votacao_votos_*`) passam a ter valores quando o registro de voto (#48)
> chamar `MetricasVotacao.votoRegistrado()` / `falhaRegistroVoto(...)`.

## 3. Quando suspender

Suspenda (estado `Suspensa`) quando a votação **não puder receber votos de forma íntegra** e a
causa não se resolver em poucos minutos:

- readiness `DOWN` (banco indisponível) por mais de 5 minutos com votação aberta;
- falhas de registro de voto não esperadas ou erro 5xx persistente;
- suspeita de fraude, de violação de sigilo ou de regra configurada errada;
- determinação da comissão/organização responsável.

Não suspenda por lentidão passageira ou por um pico isolado de erro: a suspensão é visível aos
eleitores e fica na auditoria.

Em `Suspensa` a votação não recebe votos, e o job de encerramento não leva a votação para `Apuracao`,
mesmo que a data de fim passe (SPECS §4.2).

## 4. Como suspender, retomar ou cancelar

Somente o `ADMIN_VOTACAO` da votação. Toda transição fica em `transicoes_votacao` (RNF-03), com
autor, motivo e horário. O motivo é obrigatório para suspender e para cancelar uma votação em
andamento.

```bash
TOKEN=$(curl -s -X POST http://api:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@exemplo.com","senha":"..."}' | jq -r .token)
V=<uuid-da-votacao>

# Suspender
curl -X POST http://api:8080/api/votacoes/$V/transicoes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"destino":"Suspensa","motivo":"Banco indisponível desde 14:05; incidente INC-42"}'

# Retomar, prorrogando o fim para compensar o tempo parado (opcional)
curl -X POST http://api:8080/api/votacoes/$V/transicoes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"destino":"EmVotacao","motivo":"Banco restabelecido","novoFimEm":"2026-11-20T18:00:00Z"}'

# Cancelar (irreversível: a votação não é apurada)
curl -X POST http://api:8080/api/votacoes/$V/transicoes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"destino":"Cancelada","motivo":"Integridade comprometida; nova votação será convocada"}'

# Conferir o histórico
curl http://api:8080/api/votacoes/$V/transicoes -H "Authorization: Bearer $TOKEN"
```

Se a API estiver fora do ar, a votação não recebe votos de qualquer forma; suspenda assim que ela
voltar e **antes** de reabrir o tráfego, para decidir com calma entre retomar e cancelar. Nunca
altere o estado direto no banco: a transição perderia a auditoria e as guardas.

## 5. Decidir entre retomar e cancelar

- **Retomar** quando a causa foi resolvida e nenhum voto registrado foi afetado. Se o fim já passou
  ou o tempo parado foi relevante, informe `novoFimEm` (precisa ser posterior ao fim atual e
  futuro). Retomar com o fim vencido faz o job de encerramento levá-la para `Apuracao` na próxima execução.
- **Cancelar** quando houver dúvida sobre a integridade dos votos já registrados ou sobre o sigilo.

## 6. Depois do incidente

1. Registre o `correlationId` dos erros, o intervalo e as métricas acima no relatório.
2. Comunique os eleitores com o motivo registrado na transição.
3. Abra issue com a causa raiz e a correção.
