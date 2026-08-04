# shejera-backend

Homelab API — Kotlin/Ktor, jOOQ, Flyway, PostgreSQL. Deployment via [homelab](https://github.com/okarahan/homelab) + Flux.

Frontend: [shejera-frontend](https://github.com/okarahan/shejera-frontend)

## Stack

| Komponente | Zweck |
|------------|-------|
| **Ktor** | REST API |
| **jOOQ** | Typsichere SQL-Abfragen (kein ORM) |
| **Flyway** | Schema-Versionierung |
| **PostgreSQL** | Datenbank |
| **HikariCP** | Connection Pool |

## Voraussetzungen

- **JDK 21** für den Build. Mit dem Gradle Wrapper wird JDK 21 beim ersten `./gradlew`-Lauf automatisch heruntergeladen, falls es lokal fehlt (`gradle/gradle-daemon-jvm.properties`).
- Alternativ: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (macOS) oder Temurin 21 manuell installieren.

## Lokal

Mit [Task](https://taskfile.dev):

```bash
task db      # Nur PostgreSQL
task run     # PostgreSQL + ./gradlew run (für Frontend-Dev)
task docker  # PostgreSQL + Backend-Container
task db:stop # PostgreSQL stoppen
```

Manuell:

```bash
docker compose up -d
./gradlew run
```

Health-Checks:

```bash
curl http://localhost:8080/
curl http://localhost:8080/health
curl http://localhost:8080/ready
```

## OpenAPI

Die API ist als **OpenAPI 3.0** spezifiziert:

- Datei: [`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml)
- Laufend: `http://localhost:8080/openapi.yaml`

Fürs Frontend (TypeScript-Client generieren):

```bash
# Beispiel mit openapi-generator-cli (nach npm install -g @openapitools/openapi-generator-cli)
openapi-generator-cli generate \
  -i http://localhost:8080/openapi.yaml \
  -g typescript-fetch \
  -o ../shejera-frontend/src/api/generated
```

Oder die YAML-Datei direkt aus dem Repo importieren (Swagger UI, Postman, Insomnia).

## jOOQ Codegen

Nach dem Anlegen von Flyway-Migrationen (Tabellen in der DB):

```bash
docker compose up -d
./gradlew run          # Flyway migriert beim Start
./gradlew jooqCodegen  # Generiert Kotlin-Klassen aus dem Schema
```

Generierter Code liegt unter `build/generated-src/jooq/main` (nicht committen).

Konfiguration über `gradle.properties` oder `-Pjooq.url=...`.

## Image

GitHub Actions: Push auf `main` erzeugt automatisch den nächsten Patch-Tag (`v0.1.x`), pusht das Image nach `ghcr.io/okarahan/shejera-backend`, verifiziert den Manifest-Digest und committed den neuen Image-Pin nach [homelab](https://github.com/okarahan/homelab) (`kubernetes/apps/shejera/deployment.yaml`). Flux rollt das Deployment aus.

Voraussetzung: Repo-Secret `HOMELAB_DEPLOY_TOKEN` (fine-grained PAT, nur `okarahan/homelab`, Contents: Read+Write).

Commit-Flags:
- `[skip release]` — kein Tag/Image/Deploy
- `[skip deploy]` — Tag+Image ja, Homelab-Pin nein

Umgebungsvariablen im Deployment:

| Variable | Beschreibung |
|----------|--------------|
| `DATABASE_JDBC_URL` | `jdbc:postgresql://host:5432/shejera` |
| `DATABASE_USER` | DB-Benutzer |
| `DATABASE_PASSWORD` | DB-Passwort |
| `PORT` | HTTP-Port (default: 8080) |
| `SHEJERA_INVITE_ORIGIN` | Basis-URL für Invite-Links (z. B. `http://shejera.o.karahan.de`; Pfad wird zu `/contrib/{token}`) |
| `SHEJERA_JWT_SECRET` | Secret zum Signieren/Verifizieren des Invite-JWT (HS256) |
| `SHEJERA_JWT_TTL_DAYS` | Optional: JWT TTL in Tagen (default: 30) |
| `SHEJERA_BOOTSTRAP_EMAIL` | Optional: E-Mail für Bootstrap-Admin-Invite |
| `SHEJERA_BOOTSTRAP_NAME` | Optional: Anzeigename Bootstrap-Admin |
| `SHEJERA_BOOTSTRAP_TOKEN` | Optional: fester Bootstrap-Invite-Token |
