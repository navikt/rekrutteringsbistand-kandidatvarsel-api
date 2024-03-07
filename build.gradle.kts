plugins {
    application
    kotlin("jvm") version "1.9.21"
    kotlin("kapt") version "1.9.22"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val mockOAuth2ServerVersion = "2.1.0"
val fuelVersion = "2.3.1"
val javalinVersion = "5.6.3"

application {
    mainClass.set("no.nav.toi.MainKt")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("no.nav.common:audit-log:3.2023.12.12_13.53-510909d4aa1a")
    implementation("org.codehaus.janino:janino:3.1.11")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    testImplementation("com.github.kittinunf.fuel:fuel-jackson:$fuelVersion")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock:3.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.skyscreamer:jsonassert:1.5.1")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}