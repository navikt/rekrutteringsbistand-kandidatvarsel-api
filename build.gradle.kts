plugins {
    application
    kotlin("jvm") version "2.2.21"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val mockOAuth2ServerVersion = "2.1.0"
val fuelVersion = "2.3.1"
val javalinVersion = "6.1.3"

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
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("no.nav.common:audit-log:3.2023.12.12_13.53-510909d4aa1a")
    implementation("org.codehaus.janino:janino:3.1.11")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.0.0")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-jackson:$fuelVersion")

    implementation("org.apache.kafka:kafka-clients:3.7.0")
    implementation("no.nav.tms.varsel:kotlin-builder:1.0.0")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.springframework:spring-jdbc:6.1.5")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
    implementation("com.github.navikt:rapids-and-rivers:2025110410541762250064.d7e58c3fad81")


    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock:3.3.1")
    testImplementation("org.skyscreamer:jsonassert:1.5.1")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("io.mockk:mockk:1.14.6")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}