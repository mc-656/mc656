# syntax=docker/dockerfile:1

# Imagem da API (RNF-08). Toda configuração entra por variável de ambiente
# (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, HMAC, COMPROVANTE_CHAVE):
# nenhum segredo nem .env é copiado para a imagem (ver .dockerignore). O
# springboot4-dotenv ignora a ausência do .env (ignore-if-missing=true por padrão),
# então dentro do container valem só as variáveis de ambiente.

# ---- Build: Maven wrapper + JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependências primeiro, para o cache de camada sobreviver a mudanças só no código.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/eleicao-*.jar /workspace/app.jar

# ---- Runtime: JRE 21 enxuto, usuário não-root ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S eleicao && adduser -S -G eleicao -H -s /sbin/nologin eleicao
COPY --from=build --chown=eleicao:eleicao /workspace/app.jar app.jar
USER eleicao

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# /actuator/health é público (SecurityConfig) e fica DOWN se o banco cair.
# start-period cobre o Flyway aplicando migrações no primeiro start.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
