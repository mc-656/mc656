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
