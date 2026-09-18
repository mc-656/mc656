include .env
export

.PHONY: db-up db-down db-logs db-reset db-psql

## Sobe o Postgres em background
db-up:
	docker compose up -d postgres

## Para o Postgres (mantém os dados)
db-down:
	docker compose down

## Segue os logs do Postgres
db-logs:
	docker compose logs -f postgres

## Destrói e recria o Postgres (apaga o volume)
db-reset:
	docker compose down -v
	docker compose up -d postgres

## Abre um psql no container
db-psql:
	docker compose exec postgres psql -U $(DB_USERNAME) -d $(DB_NAME)
