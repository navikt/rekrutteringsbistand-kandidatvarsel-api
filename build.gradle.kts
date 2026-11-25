plugins {
    application
    kotlin("jvm") version "2.4.21"
    id("io.github.ben-manes.versions") version "0.67.0"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val javalinVersion = "7.2.4"
val logbackVersion = "1.6.7"
val logstashLogbackEncoderVersion = "9.0"
val jacksonVersion = "2.22.3"
val jackson3Version = "3.3.0"
val javaJwtVersion = "4.6.2"
val jwksRsaVersion = "0.24.2"
val auditLogVersion = "4.2026.11.16_22.48-f6f69ab8d9b9"
val javaUuidGeneratorVersion = "5.2.0"
val kafkaClientsVersion = "4.4.0"
val tmsVarselBuilderVersion = "2.2.0"
val hikariVersion = "7.1.0"
val springJdbcVersion = "7.0.10"
val postgresqlVersion = "42.7.14"
val flywayVersion = "13.12.2"
val opentelemetryLogbackMdcVersion = "2.33.1-alpha"
val tbdLibsVersion = "20261029.1843"

val junitVersion = "6.1.3"
val systemStubsVersion = "2.1.8"
val assertjVersion = "3.27.7"
val mockOAuth2ServerVersion = "7.0.1"
val wiremockVersion = "3.13.2"
val jsonassertVersion = "1.5.3"
val testcontainersVersion = "2.0.5"
val mockkVersion = "1.14.12"

application {
    mainClass.set("no.nav.toi.kandidatvarsel.MainKt")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://maven.pkg.github.com/navikt/tms-varsel-authority")
}

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(platform("tools.jackson:jackson-bom:$jackson3Version"))
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.auth0:java-jwt:$javaJwtVersion")
    implementation("com.auth0:jwks-rsa:$jwksRsaVersion")
    implementation("no.nav.common:audit-log:$auditLogVersion")
    implementation("com.fasterxml.uuid:java-uuid-generator:$javaUuidGeneratorVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("no.nav.tms.varsel:kotlin-builder:$tmsVarselBuilderVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.springframework:spring-jdbc:$springJdbcVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:$opentelemetryLogbackMdcVersion")

    // Rapids and rivers fra tbd-libs (uten Ktor)
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-api:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:kafka:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:$systemStubsVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
