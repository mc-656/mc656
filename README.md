# mc656

Caio Maia Moreira Santos - 281749
Miguel Pereira Ramos - 281335
Lucas Gugel Maciel -260579
Vinícius Maciel de Sousa - 281391
Fernando Nagano Foscheira - 281183

## Descrição inicial do projeto:

O processo eleitoral, muitas vezes, possui nuances que acabam distanciando o eleitor, como não entender as suas regras totalmente. 
Um exemplo clássico é que diversos brasileiros não sabem que um deputado pode ser eleito com menos votos do que outro mais votado. 

Por isso, pensamos em um projeto que pode ajudar os eleitores a acompanhar totalmente o processo eleitoral. 
Além disso, ter o poder de criar a sua própria votação.

Os usuários podem utilizar o sistema para acompanhar eleições públicas, como por exemplo, eleição presidencial. 
Buscar informações sobre os candidatos, descobrir quando serão os debates e aparições públicas, ler resumos sobre os debates e últimas notícias. 
Além disso usuários podem criar suas próprias votações privadas com regras personalizadas para promover desde votações de condomínios até eleições de centro acadêmico.

## Pré-requisitos

- Java 21
- Docker e Docker Compose
- Maven Wrapper (`./mvnw`, já incluído no repositório)

## Configuração do ambiente

As credenciais do banco ficam em um arquivo `.env` na raiz do projeto. Copie o
exemplo e ajuste os valores:

```bash
cp .env.example .env
```

Variáveis disponíveis:

| Variável      | Descrição                          | Padrão    |
| ------------- | ---------------------------------- | --------- |
| `DB_USERNAME` | Usuário do Postgres                | —         |
| `DB_PASSWORD` | Senha do Postgres                  | —         |
| `DB_NAME`     | Nome do banco                      | `eleicao` |
| `DB_PORT`     | Porta do Postgres no host          | `5433`    |
| `MAIL_HOST`     | Servidor SMTP dos convites (RF-17)   | `localhost` |
| `MAIL_PORT`     | Porta SMTP                           | `1025`      |
| `MAIL_USERNAME` | Usuário SMTP (vazio = sem auth)      | —           |
| `MAIL_PASSWORD` | Senha SMTP                           | —           |
| `MAIL_FROM`     | Remetente dos convites               | `nao-responda@eleicao.local` |
| `APP_URL_BASE`  | Base dos links de convite            | `http://localhost:8080` |
| `DB_HOST`     | Host do Postgres (útil no Docker)  | `localhost` |

O Spring lê esse `.env` automaticamente (dependência `spring-dotenv`), então as
mesmas variáveis servem para o container e para a aplicação.

## Subindo o banco (Docker)

O `compose.yaml` sobe um Postgres 17 (Alpine) com volume persistente e
healthcheck. Use os atalhos do `Makefile`:

```bash
make db-up      # sobe o Postgres em background
make db-logs    # segue os logs do container
make db-psql    # abre um psql dentro do container
make db-down    # para o Postgres (mantém os dados)
make db-reset   # destrói e recria o Postgres (apaga o volume)
```

Equivalente sem o `Makefile`:

```bash
docker compose up -d postgres
docker compose down
docker compose down -v      # remove também o volume de dados
```

## E-mail de convites (Mailpit)

O envio de convites por e-mail (RF-17) usa SMTP comum (`spring-boot-starter-mail`).
Em desenvolvimento, o `compose.yaml` traz o [Mailpit](https://mailpit.axllent.org/),
um servidor SMTP falso e gratuito que captura as mensagens sem entregá-las:

```bash
docker compose up -d mailpit   # SMTP em localhost:1025
```

As mensagens aparecem em `http://localhost:8025` (ou via API em
`http://localhost:8025/api/v1/messages`). Os padrões de `MAIL_HOST`/`MAIL_PORT` já
apontam para ele; para um SMTP real basta preencher as variáveis `MAIL_*` no `.env`.

### Fluxo do convite

1. Com a votação em `Publicada` ou `EmVotacao`, o `ADMIN_VOTACAO` gera o convite:
   `PUT /api/votacoes/{id}/convite` com `{"modo": "ABERTO"}` ou `{"modo": "RESTRITO"}`.
   A resposta traz o `link` (para WhatsApp etc.).
2. QR Code em PNG: `GET /api/votacoes/{id}/convite/qrcode`.
3. Envio por e-mail: `POST /api/votacoes/{id}/convite/emails` com `{"emails": [...]}`.
   No modo restrito, só esses e-mails conseguem entrar.
4. Quem abre o link (`/convites/{token}`) sem sessão vai para o login/cadastro e volta
   ao convite já incluído como eleitor. Clientes da API usam
   `POST /api/convites/{token}/aceite` com o JWT.

## Rodando a aplicação (Spring Boot)

Com o banco no ar, inicie a aplicação:

```bash
./mvnw spring-boot:run
```

O Flyway aplica as migrations no start e o JPA valida o schema
(`ddl-auto: validate`). A API sobe em `http://localhost:8080`.

Documentação da API (Swagger UI): `http://localhost:8080/swagger-ui.html`

### Migrações de banco (Flyway)

As migrações ficam em `src/main/resources/db/migration` e são aplicadas
automaticamente no start da aplicação, antes do Hibernate validar o schema.

**Convenção de nomenclatura:**

```
V<versão>__<descricao_em_snake_case>.sql
```

- Dois underscores separam a versão da descrição: `V2__cria_tabela_usuario.sql`.
- A versão é um inteiro incremental, sem buracos e sem reuso.
- Migração já aplicada **nunca** é editada: o Flyway guarda o checksum em
  `flyway_schema_history` e a aplicação não sobe se ele divergir
  (`validate-on-migrate: true`). Correção vira uma versão nova.
- Scripts repetíveis (views, functions) usam o prefixo `R__` e rodam sempre que
  o conteúdo muda.

Para recomeçar de um banco vazio: `make db-reset`.

### Build e testes

```bash
./mvnw clean package   # gera o jar em target/
./mvnw test            # roda os testes

java -jar target/eleicao-0.0.1-SNAPSHOT.jar   # executa o jar gerado
```

## Deploy (Docker e CD)

Requisito RNF-08. Por decisão do time a aplicação roda só local por enquanto
(sem hospedagem paga); o pipeline publica a imagem e deixa o passo de deploy
pronto para um alvo genérico.

### Rodando a imagem localmente

O `Dockerfile` faz build multi-stage (Maven wrapper + JDK 21 → JRE 21 Alpine),
roda como usuário não-root `eleicao` e tem `HEALTHCHECK` em `/actuator/health`.
A configuração entra **só por variáveis de ambiente**: o `.env` não é copiado
para a imagem (`.dockerignore`) e o `springboot4-dotenv` ignora a ausência dele.

```bash
docker build -t eleicao:local .

docker network create eleicao-net
docker run -d --name eleicao-pg --network eleicao-net \
  -e POSTGRES_USER=eleicao -e POSTGRES_PASSWORD=eleicao -e POSTGRES_DB=eleicao postgres:17-alpine

docker run --rm --name eleicao-app --network eleicao-net -p 8080:8080 \
  -e DB_HOST=eleicao-pg -e DB_PORT=5432 -e DB_NAME=eleicao \
  -e DB_USERNAME=eleicao -e DB_PASSWORD=eleicao \
  -e HMAC="$(openssl rand -hex 64)" -e COMPROVANTE_CHAVE="$(openssl rand -base64 32)" \
  eleicao:local

curl localhost:8080/actuator/health   # {"status":"UP"}
```

Com o `.env` preenchido, o `compose.yaml` faz o mesmo (o serviço `app` fica
num profile, então `make db-up` continua subindo só o Postgres):

```bash
docker compose --profile app up -d --build   # API em :${APP_PORT:-8080}
```

### Como funciona o CD

- **CI** (`ci.yml`): build, lint e testes em todo PR.
- **Migrações** (`migracoes.yml`, PRs para `main`/`develop`):
  - falha se alguma migração que já existe na base do PR foi modificada,
    renomeada ou removida (só adição é permitida), se há versão duplicada ou
    nome fora do padrão. O mesmo script roda local:
    `bash .github/scripts/verifica-migracoes.sh origin/develop`;
  - sobe um Postgres 17 vazio, inicia a aplicação (Flyway aplica a cadeia
    inteira e o Hibernate valida o schema) e confere se todas as versões
    constam em `flyway_schema_history`.
- **CD** (`cd.yml`, push em `main` = produção, `develop` = homologação):
  1. build e push da imagem no GHCR (`ghcr.io/<owner>/<repo>`, gratuito, via
     `GITHUB_TOKEN`), com tags `sha-<commit>`, `<branch>` e `latest` (só `main`);
  2. job `deploy` no GitHub Environment `producao` ou `homologacao`. Se o
     environment não tiver o secret `DEPLOY_WEBHOOK_URL`, o job só registra a
     imagem e termina com sucesso. Se tiver, envia um `POST` ao webhook e faz
     health check em `DEPLOY_HEALTH_URL`; se não ficar `UP` em ~5 min o job
     falha e a versão não é considerada promovida.

### Configurando um alvo real (futuro)

Qualquer host que aceite um webhook de deploy serve (Render, Railway, Fly.io,
Coolify, um VPS com um receptor de webhook etc.). O mantenedor precisa:

1. Em *Settings → Environments*, criar `producao` e `homologacao` (o GitHub
   também cria ao primeiro uso; em `producao` vale exigir revisores).
2. Em cada environment, cadastrar os secrets:
   - `DEPLOY_WEBHOOK_URL`: endpoint que dispara o deploy;
   - `DEPLOY_WEBHOOK_TOKEN` (opcional): enviado como `Authorization: Bearer`;
   - `DEPLOY_HEALTH_URL` (secret ou variable): ex.
     `https://api.exemplo.com/actuator/health`;
   - `DEPLOY_VERSAO_URL` (variable, recomendada): URL que responde com o SHA
     do commit em execução. Como webhooks costumam ser assíncronos, logo após
     o `POST` o health ainda pode vir da versão antiga; com essa URL o job só
     aceita o deploy quando a versão nova está no ar. Sem ela, o job espera
     `DEPLOY_ESPERA_SEGUNDOS` (variable, padrão 30) e exige 3 respostas `UP`
     seguidas, o que só é confiável se o webhook for síncrono.
3. No alvo, configurar `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
   `DB_PASSWORD`, `HMAC` e `COMPROVANTE_CHAVE` como variáveis de ambiente do
   serviço (nunca no repositório) e apontar para um PostgreSQL 17 gerenciado.
4. Se o pacote do GHCR ficar privado, dar ao alvo um token com `read:packages`
   (ou tornar o pacote público em *Packages → Package settings*).

O webhook recebe:

```json
{"imagem": "ghcr.io/<owner>/<repo>:sha-abc1234", "ambiente": "producao", "sha": "<commit completo>"}
```

### Rollback

- **Aplicação:** re-implantar uma tag anterior da imagem (`sha-<commit>` de uma
  versão boa, listada em *Packages* do repositório). Com webhook configurado,
  basta reexecutar o workflow `CD` daquele commit (*Actions → CD → Re-run*) ou
  chamar o webhook com a tag antiga; localmente,
  `docker run ... ghcr.io/<owner>/<repo>:sha-<commit>`.
- **Banco:** migrações são *forward-only*. O Flyway não desfaz versões e uma
  migração aplicada nunca é editada; se a versão nova trouxe uma migração
  problemática, a correção é uma **nova** migração (`V<n+1>__...`). Antes de
  voltar a imagem, confirme que o código antigo é compatível com o schema
  atual (por isso migrações devem ser aditivas/retrocompatíveis sempre que
  possível).
