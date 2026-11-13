# Plan for migrering til Jackson 3

## Mål

Migrere prosjektets Jackson-bruk fra versjon 2 til versjon 3 i små, separat testbare iterasjoner. Før Jackson-migreringen starter, oppgraderer vi øvrige direkte avhengigheter og Gradle-plugins til nyeste offentlige stabile release.

Utvikleren oppretter brancher og pull requests manuelt.

## Forutsetninger og avklaringer

- Planen forutsetter at Jackson 2 kan beholdes midlertidig der migrering krever omfattende endringer. Dette må bekreftes.
- JSON-kontrakter, autentisering og forretningslogikk skal beholde dagens oppførsel.
- Nye felt, mottakere, logger med payloads eller tilgangsregler inngår ikke.
- Gradle Wrapper, Java-versjon, Docker-images, CI-actions og Nais-konfigurasjon oppgraderes ikke uten et dokumentert behov og egen avklaring.
- OpenTelemetry-avhengigheten bruker allerede en alpha-versjon. Dersom artefaktet ikke har en stabil release eller støttet stabil erstatning, må et unntak avklares.

## Utgangspunkt

Kartleggingen viser:

- Direkte Jackson 2 BOM på versjon `2.22.3`.
- Jackson 2-bruk i Javalin, HTTP-klienter, JSONB-håndtering og tester.
- Jackson 3-importer i fire Rapids-lyttere.
- Ben Manes-pluginen finnes allerede i bygget.
- Kotlin-pluginen er `2.4.20`, Gradle Wrapper er `9.9.1`, og JVM-toolchain er Java 25.

Oppløste avhengighetsversjoner, nyeste offentlige release-versjoner og dagens byggstatus er ikke verifisert. De kontrolleres før endringer.

## Iterasjon 0: Etabler baseline

Ingen versjonsendringer.

1. Kjør eksisterende bygg og tester.
2. Registrer produksjons- og testavhengigheter.
3. Finn hvilke biblioteker som trekker inn Jackson 2 og 3.
4. Kartlegg mapper-konfigurasjon og bibliotekseide JSON-grenser.
5. Registrer eksisterende feil separat fra oppgraderingsfeil.

```bash
./gradlew --version
./gradlew clean build
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration runtimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration testRuntimeClasspath
```

**Ferdig når:** Utgangspunktet kan reproduseres, eller konkrete blokkeringer er avklart.

## Iterasjon 1: Finn målversjoner og oppgrader Gradle-plugins

1. Bruk eksisterende Ben Manes-plugin til å finne oppgraderingskandidater.
2. Kontroller hver målversjon mot Maven Central, Gradle Plugin Portal eller bibliotekets offentlige pakkeregister og release-informasjon.
3. Avvis snapshots, milepæler, RC-er og andre prereleases.
4. Registrer nåværende versjon, målversjon, kilde og kompatibilitetskrav.
5. Oppgrader Kotlin-pluginen og Ben Manes-pluginen dersom nyere stabile versjoner finnes.
6. Behold Jackson-pinnene foreløpig.

```bash
./gradlew dependencyUpdates
./gradlew clean build
```

Kontroller Kotlin-, Gradle- og JDK-kompatibilitet før pluginoppgradering. Ikke stol på versjonsrapporten alene.

**Ferdig når:** Pluginoppgraderingene har grønt bygg, og øvrige avhengigheter har kontrollerte målversjoner.

## Iterasjon 2: Oppgrader øvrige avhengigheter

Del arbeidet i separate deliterasjoner:

| Gruppe | Avhengigheter |
|---|---|
| Testverktøy | JUnit, AssertJ, MockK, WireMock, JSONassert, System Stubs, MockOAuth2Server og Testcontainers |
| Logging og støttebiblioteker | Logback, Logstash Encoder, OpenTelemetry, UUID og audit-log |
| Databaseklienter | HikariCP, Spring JDBC, PostgreSQL-driver og Flyway |
| Integrasjoner | Javalin, Kafka-klient, TMS-varselbygger og tbd-libs |
| Tokenbiblioteker | java-jwt og jwks-rsa |

For hver deliterasjon:

1. Oppgrader til nyeste offentlige stabile release, inkludert major-versjoner.
2. Kjør bygg og relevante tester.
3. Kontroller endringer i den oppløste avhengighetsgrafen.
4. Registrer kompatibilitetsproblemer og nødvendige tilpasninger.

Flyway-modulene holdes samordnet. Det samme gjelder tbd-libs-modulene. Transitive Jackson-endringer dokumenteres selv om egne Jackson-pinner beholdes.

Omfattende inkompatibiliteter skilles ut i egne deliterasjoner. De skal ikke skjules ved å velge en eldre målversjon uten en eksplisitt avklaring.

Dersom oppgradering av tokenbiblioteker krever endringer i autentiseringskode, stoppes den deloppgaven for egen sikkerhetsavklaring.

**Ferdig når:** Alle øvrige avhengigheter er oppdatert, eller gjenstående blokkeringer er eksplisitt avklart før Jackson-migreringen starter.

## Iterasjon 3: Lås eksisterende JSON-oppførsel

Utvid eksisterende tester før mapperne byttes.

Dekningen skal omfatte:

- HTTP-requests og responser.
- Min-side-bestillinger og Kafka-oppdateringer.
- Rapid-meldinger inn og ut.
- Lesing av historisk JSONB.
- Ny JSONB som fortsatt kan leses av gammel kode.
- Feltnavn, nullverdier, enumverdier og dato-/tidssoneformat.
- Manglende og ukjente felter.
- Ugyldig JSON og relevant feilhåndtering.
- Norske feltnavn der slike finnes.

Bruk syntetiske testdata. Sammenlign JSON strukturelt, og test konkrete strengverdier der formatet er en del av kontrakten. Feltrekkefølge testes bare dersom den har betydning for en faktisk konsument.

Round-trip alene er ikke tilstrekkelig: Både skriver og leser kan endre oppførsel samtidig.

**Ferdig når:** Testene beskriver dagens eksterne og lagrede formater uten Jackson-bytte.

## Iterasjon 4: Migrer avgrensede Jackson-brukssteder

Kontroller en stabil Jackson 3-målversjon og innfør samordnet versjonsstyring med Jackson 3 BOM.

Migrer ett brukssted om gangen:

1. Request-serialisering i `KandidatsokApiKlient.kt`.
2. JSON-håndtering i `MinsideClient.kt` og `MinsideServices.kt`.
3. JSONB-håndtering i `MinsideVarsel.kt`.
4. JSON-parsing i `HttpKlient.kt` og tilknyttede brukere, uten å endre tokenhåndteringen.

Behold mapper-konfigurasjon per brukssted. Ikke innfør én global mapper som del av migreringen.

Kontroller builder-konfigurasjon og endrede standardverdier eksplisitt. Ikke erstatt pakkenavn mekanisk eller anta at grønn kompilering beviser lik oppførsel.

Jackson 2 og 3 kan sameksistere midlertidig. Dokumenter bibliotekseierskap og begrunnelse for gjenværende Jackson 2-bruk.

**Ferdig når:** Kontrakttestene viser uendret oppførsel for hvert migrert brukssted.

## Iterasjon 5: Migrer Javalin, Rapids og testhjelpere

1. Kontroller Javalins Jackson 3-støtte og adapter før mapperen byttes.
2. Bevar datoformat, nullhåndtering og HTTP-feilresponser.
3. Samordne Rapids-lyttere og testhjelpere med bibliotekets Jackson-API.
4. Kontroller alle relevante `JsonNode.map`-kall. I nyere Jackson 3 kan medlemsmetoden skygge Kotlins samlingsoperasjon.
5. Flytt testhjelpere sammen med integrasjonen, men behold uavhengige kontraktforventninger.

`com.fasterxml.jackson.annotation.*` brukes også av Jackson 3 og skal ikke behandles som gjenglemt Jackson 2-bruk.

**Ferdig når:** HTTP- og meldingsintegrasjonene har dokumentert uendret oppførsel.

## Iterasjon 6: Rydd opp

1. Fjern ubrukte direkte Jackson 2-avhengigheter.
2. Kontroller produksjons- og testclasspath på nytt.
3. Kjør hele bygget og kontroller distribusjonsartefaktet.
4. Registrer eventuell gjenværende Jackson 2-bruk med eier, begrunnelse og kriterium for fjerning.

Skill mellom disse sluttstatusene:

- Egen kode er migrert til Jackson 3.
- Jackson 2 er helt fjernet fra produksjonsclasspath.

Et tredjepartsbibliotek kan blokkere det siste uten å blokkere det første.

## Felles godkjenningskrav

Hver iterasjon skal ha:

- Grønt `./gradlew clean build`.
- Beståtte relevante kontrakt- og integrasjonstester.
- Gjennomgått kildekode- og avhengighetsdiff.
- Dokumenterte unntak og blokkeringer.
- En separat rollback-mulighet.

Tester skal ikke fjernes, hoppes over eller svekkes for å godta utilsiktede endringer.

## Observabilitet og verifikasjon etter deploy

Kontroller eksisterende målinger og driftsinformasjon for:

- HTTP-feil og deserialiseringsfeil.
- Meldingsbehandling og consumer lag.
- Feil ved lesing og skriving av JSONB.
- Varselbestilling og statusoppdateringer.

Ikke innfør logging av payloads, tokens eller personopplysninger.

## Rollback

Rull tilbake til forrige verifiserte artefakt dersom en iterasjon gir feil.

Rollback forutsetter at meldinger og lagret JSON fortsatt kan leses av gammel kode. Denne kompatibiliteten skal testes før deploy. Databaseskjemaet endres ikke som del av migreringen.

## Rød sone

Endringer i tokenvalidering, tilgangskontroll og forretningslogikk inngår ikke som mekaniske oppgraderinger. Hvis slike endringer blir nødvendige, skal de avklares separat og implementeres av utvikleren.

Mapperbytte og mekaniske tilpasninger med testet, uendret oppførsel er grønn sone.

## Ansvar

- Utvikleren oppretter brancher og pull requests manuelt.
- Hver iterasjon gjennomgås før neste starter.
- Planen innebærer ikke godkjenning av deploy eller endringer i CI, tilgangsregler eller infrastruktur.
