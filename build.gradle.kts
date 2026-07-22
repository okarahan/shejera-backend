import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import java.net.URI
import java.io.InputStream

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.8.0")
    }
}

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("io.ktor.plugin") version "3.5.1"
    id("org.jooq.jooq-codegen-gradle") version "3.21.6"
    id("org.flywaydb.flyway") version "11.8.0"
}

group = "com.shejera"
version = "0.1.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.1"
val jooqVersion = "3.21.6"
val flywayVersion = "11.8.0"
val logbackVersion = "1.5.18"
val postgresVersion = "42.7.5"
val hikaricpVersion = "6.2.1"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.jooq:jooq-kotlin:$jooqVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("org.openpnp:opencv:4.9.0-0")
    // Bundled native Tesseract + Leptonica (no system brew install required)
    implementation("org.bytedeco:tesseract-platform:5.5.0-1.5.11")

    jooqCodegen("org.postgresql:postgresql:$postgresVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}

val generatedTessdata = layout.buildDirectory.dir("generated-tessdata")
val tessdataLanguages = listOf("eng", "tur")

tasks.register("downloadTessdata") {
    description = "Download embedded Tesseract language models (tessdata_fast)"
    val outputDir = generatedTessdata.map { it.dir("tessdata").asFile }
    outputs.dir(generatedTessdata)
    doLast {
        val dir = outputDir.get()
        dir.mkdirs()
        val base = "https://github.com/tesseract-ocr/tessdata_fast/raw/main"
        for (lang in tessdataLanguages) {
            val target = dir.resolve("$lang.traineddata")
            if (target.exists() && target.length() > 0L) continue
            URI.create("$base/$lang.traineddata").toURL().openStream().use { input: InputStream ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin.srcDir("build/generated-src/jooq/main")
        resources.srcDir(generatedTessdata)
    }
}

tasks.named("processResources") {
    dependsOn("downloadTessdata")
}
jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = findProperty("jooq.url")?.toString() ?: "jdbc:postgresql://localhost:5432/shejera"
            user = findProperty("jooq.user")?.toString() ?: "shejera"
            password = findProperty("jooq.password")?.toString() ?: "shejera"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            target {
                packageName = "com.shejera.db.generated"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}

flyway {
    url = findProperty("jooq.url")?.toString() ?: "jdbc:postgresql://localhost:5432/shejera"
    user = findProperty("jooq.user")?.toString() ?: "shejera"
    password = findProperty("jooq.password")?.toString() ?: "shejera"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}

tasks.named("jooqCodegen") {
    dependsOn("flywayMigrate")
}

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}

tasks.test {
    dependsOn("jooqCodegen")
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
