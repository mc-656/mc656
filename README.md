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

Apuração de eleições privadas (#50): veja o [fluxo, snapshot e configuração do
segundo turno](docs/apuracao.md).

Comprovante anônimo de voto (#83): veja o [contrato de verificação e a dependência
da integração da apuração](docs/comprovante-anonimo.md).

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

### Demonstração rápida (dados de exemplo)

Para testar rapidamente todos os fluxos (eleição em andamento com comparecimento, convite por e-mail no Mailpit, eleição apurada pronta para publicação/homologação, verificação de comprovante e orçamento participativo), suba o banco e o Mailpit, inicie a aplicação com o agendador acelerado e execute o script de carga:

```bash
docker compose up -d postgres mailpit
./mvnw spring-boot:run -Dspring-boot.run.arguments="--votacao.agendador.intervalo=PT5S"

# Em outro terminal:
make demo
```

O script `scripts/demo.sh` cria contas de demonstração, publica votações via API real, aguarda as transições automáticas e imprime no terminal as credenciais, links diretos e comprovantes criptografados prontos para uso.

Contas criadas pelo script (senha padrão `Senha123!`, alterável via `DEMO_SENHA`):

| Papel no cenário | Nome | E-mail |
| --- | --- | --- |
| Administradora das votações | Ana Organizadora | `admin@demo.unicamp.br` |
| Eleitor que já votou (gera comparecimento/comprovantes) | Bruno Eleitor | `eleitor1@demo.unicamp.br` |
| Eleitora apta para votar ao vivo | Carla Eleitora | `eleitor2@demo.unicamp.br` |
| Convidado por e-mail no Mailpit (RF-17) | Diego Convidado | `eleitor3@demo.unicamp.br` |

#### O que fazer em cada cenário

1. **Eleições Públicas (sem login):** acesse `http://localhost:8080/eleicoes-publicas`, filtre eleições por situação ou cargo, abra o perfil de um candidato (histórico, propostas, agenda e fontes), use o comparador lado a lado (`/comparar`) e consulte notícias, debates e conteúdos didáticos.
2. **Cenário A — *Eleição do Centro Acadêmico 2026* (`EmVotacao`):**
   - Entre como `admin@demo.unicamp.br` e abra o painel da votação para acompanhar o **comparecimento em tempo real** e o status do quórum (1/2 votantes = 50%).
   - Entre como `eleitor2@demo.unicamp.br` (em aba anônima ou após sair), acesse a cédula de votação, registre o voto em uma chapa e guarde o **comprovante anônimo criptografado** gerado.
   - Abra `http://localhost:8025` (Mailpit) para ver o e-mail de convite enviado para `eleitor3@demo.unicamp.br`; abra o link `/convites/{token}` logado como `eleitor3@demo.unicamp.br` para ingressar automaticamente como eleitor e votar.
3. **Cenário B — *Consulta do Conselho do Instituto 2026* (`Apuracao`):**
   - Entre como `admin@demo.unicamp.br`, abra `/votacoes/{id}/resultado` para conferir o resultado prévio calculado pelo motor de apuração, clique em **Publicar resultado** (`Encerrada` — liberando a consulta aos eleitores) e, em seguida, informe uma observação e clique em **Homologar resultado** (`Homologada`).
4. **Verificação de comprovante (`/comprovantes/verificar`):**
   - Cole os comprovantes impressos no terminal pelo `make demo`: o do **Cenário A** indica voto registrado aguardando apuração, enquanto o do **Cenário B** confirma que a cédula foi **incluída na apuração oficial**.
5. **Cenário C — *Orçamento Participativo do Campus 2026* (`EmVotacao`):**
   - Entre como `eleitor2@demo.unicamp.br`, abra a votação e distribua seu saldo de pontos (até `500.000` pontos, equivalente ao orçamento total de R$ 500.000,00) entre os 4 projetos cadastrados.

#### Resetando o banco (limpar usuários e votações)

Para apagar todos os usuários, votações, votos e e-mails capturados e voltar a um banco limpo (mantendo apenas a carga inicial de Eleições Públicas das migrações Flyway), pare a aplicação (`Ctrl+C`), destrua o volume do Docker e suba os serviços novamente:

```bash
docker compose down -v
docker compose up -d postgres mailpit
./mvnw spring-boot:run -Dspring-boot.run.arguments="--votacao.agendador.intervalo=PT5S"
```

*(Atalho equivalente apenas para o Postgres: `make db-reset`.)*

## Deploy (Docker e CD)

Requisito RNF-08. O pipeline publica a imagem e implanta numa VPS própria
(stack em `deploy/`: Compose atrás do Caddy da VPS, com HTTPS e domínio gratuito via
[nip.io](https://nip.io)), sem custo de hospedagem; um alvo genérico por
webhook também é suportado.

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
  2. job `deploy` no GitHub Environment `producao` ou `homologacao`. Com o
     secret `DEPLOY_SSH_HOST`, implanta na VPS (abaixo); com
     `DEPLOY_WEBHOOK_URL`, envia um `POST` ao webhook. Sem nenhum dos dois, só
     registra a imagem e termina com sucesso. Com `DEPLOY_HEALTH_URL`, faz
     health check na URL pública; se não ficar `UP` em ~5 min o job falha e a
     versão não é considerada promovida.

### Deploy em VPS

Stack em `deploy/`: Postgres 17 e a API (imagem do GHCR), sem porta
publicada. HTTPS e portas 80/443 ficam com o **Caddy central** que já atende
os outros domínios da VPS: a API entra numa rede Docker exclusiva com ele
(`eleicao-proxy`) e o site é um bloco no Caddyfile dele
(`deploy/Caddyfile.site`). Sem domínio próprio, o [nip.io](https://nip.io)
resolve `eleicao.<ip>.nip.io` para `<ip>`. O `deploy.sh` grava a tag no `.env`, sobe
com `docker compose up --wait` (espera o `HEALTHCHECK`) e, se a versão nova
não ficar saudável, volta para a anterior.

**Modelo de segurança.** Quem pode mexer no repositório (e, portanto, nos
workflows e nos secrets do environment) não deve ganhar acesso à VPS. Por isso:

- a chave de deploy tem **comando forçado** no `authorized_keys`: com ela só
  se executa o `deploy.sh`, que aceita apenas `ghcr.io/mc-656/mc656:sha-<hex>`.
  Sem shell, sem port forwarding, sem copiar arquivos;
- a stack (`compose.yaml`, `deploy.sh`) e o Caddy central são instalados e
  atualizados **pelo dono da VPS**, revisando o diff; o CD não copia nada para lá;
- a API roda confinada (`cap_drop: ALL`, `no-new-privileges`, sem volume do
  host, teto de memória/CPU/processos). Uma imagem maliciosa ainda controla a
  própria aplicação e o banco dela e tem acesso à rede, então não deixe
  serviços sem autenticação escutando em `0.0.0.0` na mesma VPS;
- a rede com o Caddy é **só dela**: na rede compartilhada do Caddy, a API
  alcançaria os backends dos outros sites sem passar pelo proxy.

**Requisitos da VPS:** Linux com Docker e o plugin Compose, ~1 GB de RAM
livre (JVM + Postgres), um Caddy em container já publicando 80/443 e a porta
do SSH aberta para o GitHub Actions.

**Preparação (uma vez, como root/sudo na VPS):**

```bash
# usuário dedicado, sem senha; o grupo docker é necessário para o compose
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy

# stack: arquivos do dono da VPS, só leitura para o deploy (exceto o .env)
sudo install -d -o root -g deploy -m 750 /home/deploy/eleicao
cd /home/deploy/eleicao
sudo curl -fsSLO https://raw.githubusercontent.com/mc-656/mc656/main/deploy/compose.yaml
sudo curl -fsSLO https://raw.githubusercontent.com/mc-656/mc656/main/deploy/deploy.sh
sudo curl -fsSL  https://raw.githubusercontent.com/mc-656/mc656/main/deploy/.env.example -o .env
sudo chown root:deploy compose.yaml deploy.sh .env
sudo chmod 644 compose.yaml && sudo chmod 755 deploy.sh && sudo chmod 660 .env
sudoedit .env   # APP_URL_BASE=https://eleicao.<ip>.nip.io, segredos

# rede exclusiva entre a API e o Caddy central
sudo docker network create eleicao-proxy
```

No **Caddy central**:

1. Ligue-o à rede de forma persistente: no `compose.yaml` dele, acrescente
   `eleicao-proxy` em `networks:` do serviço e declare-a no fim como
   `external: true` (só `docker network connect` se perde quando o container
   do Caddy é recriado).
2. Cole o bloco de `deploy/Caddyfile.site` no Caddyfile dele, trocando o IP.
3. Recarregue: `docker compose up -d` (por causa da rede nova) e, nas
   próximas mudanças só de Caddyfile,
   `docker exec <container-do-caddy> caddy reload --config /etc/caddy/Caddyfile`.

Leia os arquivos baixados antes de usar. Para atualizar a stack depois, repita
o `curl` do arquivo alterado, revisando o diff do PR que o mudou.

**Chave de deploy (na sua máquina):**

```bash
ssh-keygen -t ed25519 -N "" -C "deploy-eleicao" -f deploy_eleicao
ssh-keyscan -p 22 <ip>   # saída vai para DEPLOY_SSH_KNOWN_HOSTS
```

Na VPS, em `/home/deploy/.ssh/authorized_keys` (dono `deploy`, modo 600),
uma linha só, com o conteúdo de `deploy_eleicao.pub` no fim:

```text
command="/home/deploy/eleicao/deploy.sh",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ssh-ed25519 AAAA... deploy-eleicao
```

Teste: `ssh -i deploy_eleicao deploy@<ip> ls` deve responder
`Imagem recusada: 'ls'`, o que mostra que a chave não abre shell.

**No GitHub** (*Settings → Environments → `producao`*):

| Nome | Tipo | Valor |
|---|---|---|
| `DEPLOY_SSH_HOST` | secret | IP da VPS |
| `DEPLOY_SSH_USER` | secret | `deploy` |
| `DEPLOY_SSH_KEY` | secret | conteúdo de `deploy_eleicao` (chave privada) |
| `DEPLOY_SSH_KNOWN_HOSTS` | secret | saída do `ssh-keyscan` |
| `DEPLOY_HEALTH_URL` | variable | `https://eleicao.<ip>.nip.io/actuator/health` |
| `DEPLOY_SSH_PORT` | variable (opcional) | porta do SSH, padrão `22` |

Recomendado no environment: *Deployment branches* só `main` e *Required
reviewers* com o dono da VPS. Isso evita deploys de branches arbitrárias, mas
admins do repositório podem desfazer; a garantia de verdade é o comando forçado.

O próximo push em `main` implanta. O job faz login no GHCR com o
`GITHUB_TOKEN` do próprio job (enviado pelo stdin do SSH e descartado no fim),
então o pacote pode continuar privado. Para `homologacao` na mesma VPS seria
preciso outra porta/domínio no Caddy; por ora, configure só `producao`.

**Operação na VPS** (em `/home/deploy/eleicao`, como `deploy` ou root):
`docker compose ps`, `docker compose logs -f app`; backup do banco:
`docker compose exec -T postgres pg_dump -U eleicao eleicao | gzip > backup-$(date +%F).sql.gz`.
Deploy à mão (pacote privado exige antes `docker login ghcr.io` com um token
`read:packages`): `./deploy.sh ghcr.io/mc-656/mc656:sha-<commit>`.

### Alvo genérico (webhook)

Qualquer host que aceite um webhook de deploy serve (Render, Railway, Coolify
etc.). O mantenedor precisa:

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
  versão boa, listada em *Packages* do repositório). Com SSH ou webhook
  configurado, basta reexecutar o workflow `CD` daquele commit (*Actions → CD →
  Re-run*); na VPS, `./deploy.sh <imagem-antiga>`; com webhook, chamá-lo com a
  tag antiga; localmente,
  `docker run ... ghcr.io/<owner>/<repo>:sha-<commit>`.
- **Banco:** migrações são *forward-only*. O Flyway não desfaz versões e uma
  migração aplicada nunca é editada; se a versão nova trouxe uma migração
  problemática, a correção é uma **nova** migração (`V<n+1>__...`). Antes de
  voltar a imagem, confirme que o código antigo é compatível com o schema
  atual (por isso migrações devem ser aditivas/retrocompatíveis sempre que
  possível).
