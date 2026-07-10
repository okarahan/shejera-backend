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

- JDK 21 (für `./gradlew`; das Docker-Image bringt JDK 21 mit)

## Lokal

PostgreSQL starten:

```bash
docker compose up -d
```

App starten:

```bash
./gradlew run
```

Health-Checks:

```bash
curl http://localhost:8080/
curl http://localhost:8080/health
curl http://localhost:8080/ready
```

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

GitHub Actions pusht nach `ghcr.io/okarahan/shejera-backend` bei Push auf `main` oder Tags `v*`.

Umgebungsvariablen im Deployment:

| Variable | Beschreibung |
|----------|--------------|
| `DATABASE_JDBC_URL` | `jdbc:postgresql://host:5432/shejera` |
| `DATABASE_USER` | DB-Benutzer |
| `DATABASE_PASSWORD` | DB-Passwort |
| `PORT` | HTTP-Port (default: 8080) |

## Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Image-Tag in `homelab/kubernetes/apps/shejera-backend/deployment.yaml` setzen.
