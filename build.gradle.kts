plugins {
    application
    kotlin("jvm") version "2.4.20"
    id("io.github.ben-manes.versions") version "0.64.0"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val javalinVersion = "7.2.3"
val logbackVersion = "1.6.4"
val logstashLogbackEncoderVersion = "9.0"
val jacksonVersion = "2.22.3"
val javaJwtVersion = "4.6.1"
val jwksRsaVersion = "0.24.1"
val auditLogVersion = "4.2026.09.24_06.17-80dfc0eacb29"
val javaUuidGeneratorVersion = "5.2.0"
val kafkaClientsVersion = "4.3.1"
val tmsVarselBuilderVersion = "2.2.0"
val hikariVersion = "7.1.0"
val springJdbcVersion = "7.0.9"
val postgresqlVersion = "42.7.13"
val flywayVersion = "13.8.0"
val opentelemetryLogbackMdcVersion = "2.31.1-alpha"
val tbdLibsVersion = "20260917.2152"

val junitVersion = "5.14.4"
val systemStubsVersion = "2.1.8"
val assertjVersion = "3.27.7"
val mockOAuth2ServerVersion = "6.0.3"
val wiremockVersion = "3.13.2"
val jsonassertVersion = "1.5.3"
val testcontainersVersion = "2.0.5"
val mockkVersion = "1.14.11"

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
