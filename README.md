# mc656

Caio Maia Moreira Santos - 281749
Miguel Pereira Ramos - 281335
Lucas Gugel Maciel -260579
Vinícius Maciel de Sousa - 281391


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

## Rodando a aplicação (Spring Boot)

Com o banco no ar, inicie a aplicação:

```bash
./mvnw spring-boot:run
```

O Flyway aplica as migrations no start e o JPA valida o schema
(`ddl-auto: validate`). A API sobe em `http://localhost:8080`.

Documentação da API (Swagger UI): `http://localhost:8080/swagger-ui.html`

### Build e testes

```bash
./mvnw clean package   # gera o jar em target/
./mvnw test            # roda os testes

java -jar target/eleicao-0.0.1-SNAPSHOT.jar   # executa o jar gerado
```