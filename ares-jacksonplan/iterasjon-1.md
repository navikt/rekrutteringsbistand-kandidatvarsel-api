# Iterasjon 1: målversjoner og Gradle-plugins

## Status

Fullført 18. november 2026. Målversjonene nedenfor er kontrollert mot pakkeregistrenes metadata og publiserte POM-filer. De to pluginene er oppgradert, og rent bygg og tester er grønne.

Eksisterende endringer i `plan.md` og `iterasjon-0.md` fra avslutningen av iterasjon 0 beholdes. Utvikleren oppretter brancher og pull requests manuelt.

## Avgrensning

Denne iterasjonen oppgraderer bare de eksisterende Kotlin- og Ben Manes-pluginene. Øvrige direkte avhengigheter får kontrollerte målversjoner for iterasjon 2. Jackson-pinner, Gradle Wrapper, Java 25, CI, Docker og Nais beholdes uendret.

Prosjektet har ingen `gradle.lockfile`, `gradle/dependency-locks/` eller versjonskatalog. Vi utfører derfor ikke en automatisk masseoppgradering av bibliotekene og innfører ikke dependency locking i denne iterasjonen. Versjonene til pluginene er eksplisitt angitt i `build.gradle.kts`.

## Metode og kilder

`./gradlew dependencyUpdates --console=plain` fullførte med exitkode 0 både før og etter oppgraderingen. Pluginen bruker ikke et eget stabilitetsfilter i dette prosjektet, og rapporten inneholder prerelease-versjoner. Rapporten er brukt som kandidatliste, ikke som fasit for målversjoner.

For hvert artefakt er versjonslisten lest fra `maven-metadata.xml`. Snapshots, alpha, beta, RC, milepæler og andre prereleases er utelatt, med det avtalte OpenTelemetry-unntaket. Deretter er POM-filen for valgt målversjon hentet og XML-parset. Alle de 31 POM-kontrollene fullførte med HTTP 200.

Kildene i tabellene betyr:

- **C** er Maven Central: `https://repo.maven.apache.org/maven2`.
- **M** er prosjektets eksisterende GitHub Packages-speil: `https://github-package-registry-mirror.gc.nav.no/cached/maven-release`.
- **P** er Gradle Plugin Portal: `https://plugins.gradle.org/m2`.

For koordinatene `gruppe:artefakt:versjon` er de kontrollerte adressene:

```text
<register>/<gruppe med punktum erstattet av skråstrek>/<artefakt>/maven-metadata.xml
<register>/<gruppe med punktum erstattet av skråstrek>/<artefakt>/<versjon>/<artefakt>-<versjon>.pom
```

Nav-artefaktene i kilde M er verifisert gjennom speilet som bygget allerede bruker, ikke som Maven Central-publikasjoner. Listen er et øyeblikksbilde av tilgjengelige versjoner, ikke et løfte om at alle biblioteksoppgraderingene er kompatible med applikasjonen. Kompatibilitet og oppløste avhengigheter testes gruppevis i iterasjon 2.

## Plugins

| Plugin | Fra | Målversjon | Kilde |
|---|---|---|---|
| `org.jetbrains.kotlin.jvm` | `2.4.20` | `2.4.21` | P |
| `io.github.ben-manes.versions` | `0.64.0` | `0.67.0` | P |
| `application` | Innebygd i Gradle `9.9.1` | Uendret | Gradle Wrapper |

Markørartefaktene for pluginene er kontrollert på disse adressene:

- [Kotlin-versjoner](https://plugins.gradle.org/m2/org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/maven-metadata.xml)
- [Kotlin 2.4.21 POM](https://plugins.gradle.org/m2/org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/2.4.21/org.jetbrains.kotlin.jvm.gradle.plugin-2.4.21.pom)
- [Ben Manes-versjoner](https://plugins.gradle.org/m2/io/github/ben-manes/versions/io.github.ben-manes.versions.gradle.plugin/maven-metadata.xml)
- [Ben Manes 0.67.0 POM](https://plugins.gradle.org/m2/io/github/ben-manes/versions/io.github.ben-manes.versions.gradle.plugin/0.67.0/io.github.ben-manes.versions.gradle.plugin-0.67.0.pom)

Kotlin `2.5.0-RC` er avvist fordi den ikke er en stabil release. Ingen nye plugins legges til.

### Kompatibilitet og kjent begrensning

[Kotlins kompatibilitetsmatrise](https://kotlinlang.org/docs/gradle-configure-project.html) oppgir Gradle `7.6.3–9.7.0` som fullt støttet for Kotlin Gradle Plugin `2.4.20–2.4.21`. Prosjektets Gradle `9.9.1` ligger utenfor dette intervallet både før og etter oppgraderingen. Grønne bygg dokumenterer at kombinasjonen fungerer i dette prosjektet, ikke full leverandørstøtte.

Java 25 er støttet som Gradle-runtime fra Gradle `9.1.0`, se [Gradles kompatibilitetsmatrise](https://docs.gradle.org/9.9.1/userguide/compatibility.html). JVM-mål 25 støttes av Kotlin, se [kompilatorvalgene](https://kotlinlang.org/docs/gradle-compiler-options.html).

[Ben Manes 0.67.0](https://github.com/ben-manes/gradle-versions-plugin/releases/tag/v0.67.0) er bygget med Gradle `9.8.2`, men dette dokumenterer ikke spesifikk støtte for `9.9.1`. Både `dependencyUpdates` og det avtalte prosjektbygget er derfor kjørt etter oppgraderingen. Fra pluginversjon `0.66.0` er standardrapporteringen `plain,problems`; ingen eksisterende rapportparser er endret.

Kildene for Kotlin-releasen er [releaseoversikten](https://kotlinlang.org/docs/releases.html) og [release-notatene for 2.4.21](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21). Wrapperen er ikke endret for å tilpasse seg matrisen.

## Målversjoner for produksjonsavhengigheter

Ingen av bibliotekspinnene i denne tabellen endres i iterasjon 1.

| Koordinat | Nåværende versjon | Mål for iterasjon 2 | Kilde |
|---|---|---|---|
| `io.javalin:javalin` | `7.2.3` | `7.2.4` | C |
| `ch.qos.logback:logback-classic` | `1.6.4` | `1.6.7` | C |
| `net.logstash.logback:logstash-logback-encoder` | `9.0` | `9.0`, allerede nyeste stabile | C |
| `com.auth0:java-jwt` | `4.6.1` | `4.6.2` | C |
| `com.auth0:jwks-rsa` | `0.24.1` | `0.24.2` | C |
| `no.nav.common:audit-log` | `4.2026.09.24_06.17-80dfc0eacb29` | `4.2026.11.16_22.48-f6f69ab8d9b9` | M |
| `com.fasterxml.uuid:java-uuid-generator` | `5.2.0` | `5.2.0`, allerede nyeste stabile | C |
| `org.apache.kafka:kafka-clients` | `4.3.1` | `4.4.0` | C |
| `no.nav.tms.varsel:kotlin-builder` | `2.2.0` | `2.2.0`, allerede nyeste tilgjengelige stabile | M |
| `com.zaxxer:HikariCP` | `7.1.0` | `7.1.0`, allerede nyeste stabile | C |
| `org.springframework:spring-jdbc` | `7.0.9` | `7.0.10` | C |
| `org.postgresql:postgresql` | `42.7.13` | `42.7.14` | C |
| `org.flywaydb:flyway-core` | `13.8.0` | `13.12.2` | C |
| `org.flywaydb:flyway-database-postgresql` | `13.8.0` | `13.12.2` | C |
| `io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0` | `2.31.1-alpha` | `2.33.1-alpha`, avtalt unntak | C |
| `com.github.navikt.tbd-libs:rapids-and-rivers` | `20260917.2152` | `20261029.1843` | M |
| `com.github.navikt.tbd-libs:rapids-and-rivers-api` | `20260917.2152` | `20261029.1843` | M |
| `com.github.navikt.tbd-libs:kafka` | `20260917.2152` | `20261029.1843` | M |

Jackson BOM, Kotlin-modulen og JSR-310-modulen beholdes på `2.22.3`. De skal ikke oppgraderes sammen med de øvrige bibliotekene. Kotlin-stdlib følger Kotlin-pluginen og skal ikke få en separat versjonspinn.

## Målversjoner for testavhengigheter

| Koordinat | Nåværende versjon | Mål for iterasjon 2 | Kilde |
|---|---|---|---|
| `com.github.navikt.tbd-libs:rapids-and-rivers-test` | `20260917.2152` | `20261029.1843` | M |
| `org.junit:junit-bom` | `5.14.4` | `6.1.3` | C |
| `org.junit.jupiter:junit-jupiter` | `5.14.4`, fra BOM | `6.1.3`, fra ny BOM | C |
| `org.junit.platform:junit-platform-launcher` | `1.14.4`, fra BOM | `6.1.3`, fra ny BOM | C |
| `uk.org.webcompere:system-stubs-jupiter` | `2.1.8` | `2.1.8`, allerede nyeste stabile | C |
| `org.assertj:assertj-core` | `3.27.7` | `3.27.7`, allerede nyeste stabile | C |
| `no.nav.security:mock-oauth2-server` | `6.0.3` | `7.0.1` | C |
| `org.wiremock:wiremock-standalone` | `3.13.2` | `3.13.2`, allerede nyeste stabile | C |
| `org.skyscreamer:jsonassert` | `1.5.3` | `1.5.3`, allerede nyeste stabile | C |
| `org.testcontainers:testcontainers-postgresql` | `2.0.5` | `2.0.5`, allerede nyeste stabile | C |
| `io.mockk:mockk` | `1.14.11` | `1.14.12` | C |

## Avviste kandidater og særskilte hensyn

- Audit-log-kandidaten `4.2026.11.18_02.08-9621020fa1ca-beta` i Ben Manes-rapporten er avvist. Målet er siste tilgjengelige versjon uten prerelease-markør. Maven Central har bare eldre audit-log-publikasjoner. Vi bruker ikke den kilden til å foreslå en nedgradering.
- Spring JDBC `7.1.0-RC2`, JUnit `6.2.0-M1`, AssertJ `4.0.0-M1`, JSONassert `2.0-rc1` og WireMock `4.0.0-beta.44` er avvist.
- Metadata for OpenTelemetry Logback MDC inneholder ingen stabile versjoner. Det avtalte alpha-unntaket gjelder derfor denne målversjonen. Unntaket utvides ikke til andre biblioteker.
- JUnit og MockOAuth2Server får major-oppgraderinger i iterasjon 2. At POM-ene finnes, er ikke tilstrekkelig godkjenning av kompatibiliteten.
- Flyway-modulene oppgraderes samlet. De fire tbd-libs-modulene holdes også samordnet. Transitive Jackson-endringer må sammenlignes med baseline.
- POM-ene for tbd-libs-målet refererer til Jackson 2 BOM `2.22.4` og Jackson 3 BOM `3.2.4`. MockOAuth2Server `7.0.1` refererer til Jackson 3 `3.3.0`. Uendrede direkte Jackson-pinner garanterer derfor ikke uendrede oppløste versjoner i iterasjon 2. Produksjons- og testclasspath må kontrolleres separat ved disse oppgraderingene.
- Tokenbibliotekene oppgraderes uten endrede valideringsregler. Hvis det kreves endringer i autentiseringskode, stoppes den deloppgaven for egen sikkerhetsavklaring.

## Verifikasjon

Arbeidsagenten kjørte et grønt bygg på 47 sekunder. Koordinatoren gjennomgikk diffen og kjørte deretter den samme kommandoen selv:

```bash
./gradlew clean build --warning-mode fail --configuration-cache -Dorg.gradle.configuration-cache.parallel=true
```

Resultat fra koordinatorens kjøring:

- Exitkode 0 og `BUILD SUCCESSFUL in 28s`.
- 206 tester i 24 testklasser, ingen feil og ingen hoppede tester.
- Alle ti aktive oppgaver ble kjørt. Configuration cache ble gjenbrukt.
- Eksisterende advarsler om `asText`, parameternavn og JVM class-data sharing er uendret. Tester eller advarselskrav er ikke svekket.

`dependencyUpdates` ble kjørt på nytt med Ben Manes `0.67.0` og fullførte med exitkode 0. Rapporten ligger lokalt i `build/dependencyUpdates/report.txt`.

Begge runtime-konfigurasjonene er hentet på nytt og sammenlignet med iterasjon 0:

- Produksjon: `org.jetbrains.kotlin:kotlin-stdlib` endres fra `2.4.20` til `2.4.21` som følge av Kotlin-pluginen.
- Test: Samme stdlib-endring og tilsvarende versjonsendring i constraint for `kotlin-stdlib-common`.
- Jackson 2 er fortsatt `2.22.3`, Jackson 3 er fortsatt `3.2.2`, og delte annotasjoner er fortsatt `2.22` i begge konfigurasjonene.
- Ingen andre valgte modulversjoner er endret, og ingen uoppløste avhengigheter ble rapportert.

De nye rårapportene ligger lokalt i `build/reports/jackson-iteration-1/{runtimeClasspath,testRuntimeClasspath}.txt`. Rapporter under `build/` slettes ved neste `clean`.

## Endringskontroll og rollback

Den eneste endringen i byggkonfigurasjonen er de to pluginversjonene på linje 3–4 i `build.gradle.kts`. Det er ingen endring i applikasjonskode, JSON-kontrakter, autentisering, databaseskjema, wrapper, Java-versjon eller CI. Eksisterende logger og målinger beholdes, og det innføres ingen logging av payloads eller personopplysninger.

Rollback gjøres ved å sette de to pluginversjonene tilbake til `2.4.20` og `0.64.0` og kjøre den avtalte byggkommandoen. Ingen data må tilbakeføres. Ingen deploy er utført som del av iterasjonen.

| Perspektiv | Vurdering og grunnlag |
|---|---|
| Sikkerhet | Ingen endring i auth, tilgang eller dataflyt. Ingen nye avhengigheter er lagt til. |
| Plattform | Bygg og `dependencyUpdates` fungerer med dagens Gradle og Java. Begrensningen i dokumentert pluginstøtte er beskrevet over. |
| Arkitektur | Kun eksisterende plugins er oppgradert. Biblioteksoppgraderingene er skilt ut til iterasjon 2. |
| Endringssikkerhet | To grønne bygg etter endringen, 206 beståtte tester og kontrollert classpath-diff. |
| Brukeropplevelse | Ingen endring i brukerflyten eller brukerrettet tekst. UI er ikke endret eller visuelt testet. |

Konklusjon: Iterasjon 1 er fullført med kjent begrensning i dokumentert støtte for Gradle `9.9.1`. Iterasjon 2 er ikke startet. Ingen branch eller pull request er opprettet.

🔴 Rød sone: ingen for denne oppgaven.
