# Iterasjon 2: øvrige avhengigheter

## Status og avgrensning

Fullført 18. november 2026. Øvrige direkte avhengigheter er oppgradert i fem grupper. Målversjonene fra [iterasjon 1](iterasjon-1.md) er kontrollert på nytt mot registermetadata og POM-filer før endringene. De er uendret.

Utgangspunktet er commit `e57ca0b00f50284f634b7c721d1a082502277fa0`, og arbeidskopien var ren ved starten. Kotlin-plugin `2.4.21`, Ben Manes-plugin `0.67.0`, Gradle Wrapper `9.9.1` og Java 25 beholdes. Direkte Jackson-pinner beholdes, men oppløste Jackson-versjoner kan endres transitivt. Ingen endring i autentiseringsregler, forretningslogikk, JSON-kontrakter, databaseskjema, CI, Docker eller Nais inngår.

Utvikleren oppretter brancher og pull requests manuelt.

## Kontrollerte oppgraderinger

Alle målversjonene nedenfor finnes i registermetadata, og tilhørende POM-filer ble hentet med HTTP 200. Kildene og adresseformatet er dokumentert i [iterasjon 1](iterasjon-1.md#metode-og-kilder). Nav-artefakter er kontrollert gjennom pakkespeilet som prosjektet allerede bruker.

| Gruppe | Avhengighet | Fra | Til |
|---|---|---|---|
| Testverktøy | JUnit BOM | `5.14.4` | `6.1.3` |
| Testverktøy | MockOAuth2Server | `6.0.3` | `7.0.1` |
| Testverktøy | MockK | `1.14.11` | `1.14.12` |
| Logging og støtte | Logback Classic | `1.6.4` | `1.6.7` |
| Logging og støtte | audit-log | `4.2026.09.24_06.17-80dfc0eacb29` | `4.2026.11.16_22.48-f6f69ab8d9b9` |
| Logging og støtte | OpenTelemetry Logback MDC | `2.31.1-alpha` | `2.33.1-alpha` |
| Databaseklienter | Spring JDBC | `7.0.9` | `7.0.10` |
| Databaseklienter | PostgreSQL-driver | `42.7.13` | `42.7.14` |
| Databaseklienter | Flyway Core og PostgreSQL-modul | `13.8.0` | `13.12.2` |
| Integrasjoner | Javalin | `7.2.3` | `7.2.4` |
| Integrasjoner | Kafka Clients | `4.3.1` | `4.4.0` |
| Integrasjoner | tbd-libs Rapids, API, Kafka og testmodul | `20260917.2152` | `20261029.1843` |
| Tokenbiblioteker | java-jwt | `4.6.1` | `4.6.2` |
| Tokenbiblioteker | jwks-rsa | `0.24.1` | `0.24.2` |

OpenTelemetry-artefaktet har fortsatt bare alpha-publikasjoner. Det avtalte unntaket brukes bare for dette artefaktet. Beta-kandidaten for audit-log er avvist.

## Avhengigheter som beholdes

Følgende er allerede nyeste tilgjengelige stabile versjon i registrene som er kontrollert:

- Logstash Logback Encoder `9.0`.
- Java UUID Generator `5.2.0`.
- TMS Kotlin Builder `2.2.0`.
- HikariCP `7.1.0`.
- System Stubs Jupiter `2.1.8`.
- AssertJ `3.27.7`.
- WireMock Standalone `3.13.2`.
- JSONassert `1.5.3`.
- Testcontainers PostgreSQL `2.0.5`.

Jackson BOM og egne Jackson-moduler beholdes på direkte pinne `2.22.3`. De versjonsløse JUnit-modulene følger fortsatt JUnit BOM, uten nye separate pinner.

## Verifikasjon per gruppe

Hver gruppe skal verifiseres med følgende kommando før neste gruppe starter:

```bash
./gradlew clean build --warning-mode fail --configuration-cache -Dorg.gradle.configuration-cache.parallel=true
```

| Gruppe | Resultat fra koordinatorens bygg | Tester |
|---|---|---|
| Testverktøy | Grønt, 26 sekunder, configuration cache gjenbrukt | 206, ingen feil eller hoppede tester |
| Logging og støtte | Grønt, 25 sekunder, configuration cache gjenbrukt | 206, ingen feil eller hoppede tester |
| Databaseklienter | Grønt, 25 sekunder, configuration cache gjenbrukt | 206, ingen feil eller hoppede tester |
| Integrasjoner | Grønt, 26 sekunder, configuration cache gjenbrukt | 206, ingen feil eller hoppede tester |
| Tokenbiblioteker | Grønt, 27 sekunder, configuration cache lagret | 206, ingen feil eller hoppede tester |

Ved feil beholder vi testforventningene. Vi avklarer nødvendige endringer i autentisering eller forretningslogikk separat i stedet for å ta dem med som mekaniske oppgraderinger.

## Transitive Jackson-endringer

Før iterasjonen er Jackson 2 `2.22.3`, Jackson 3 `3.2.2` og delte annotasjoner `2.22` oppløst på både produksjons- og testclasspath. MockOAuth2Server-målet refererer til Jackson 3 `3.3.0`, mens tbd-libs-målet refererer til Jackson 2 BOM `2.22.4` og Jackson 3 BOM `3.2.4`.

Endelige oppløste versjoner og eventuelle forskjeller mellom produksjon og tester registreres etter byggene. En uendret direkte BOM-pinne er ikke en låsing av den transitive versjonen.

Etter testverktøygruppen er produksjonsclasspath uendret for Jackson. På testclasspath øker Jackson 3 til `3.3.0`, og de delte annotasjonene øker til `2.23`. Jackson 2 er fortsatt `2.22.3`. Kommandoene som genererte avhengighetsrapportene, fullførte uten uoppløste avhengigheter. Ingen testkode måtte endres for JUnit 6 eller MockOAuth2Server 7.

Logging- og støttegruppen gir ingen ytterligere Jackson-endring. De nye Logback-, audit-log- og OpenTelemetry-versjonene er bekreftet på både produksjons- og testclasspath. Eksisterende loggingkonfigurasjon er uendret.

Databasegruppen gir heller ingen ytterligere Jackson-endring. Spring JDBC, PostgreSQL-driveren og begge Flyway-modulene er bekreftet på målversjonene i begge konfigurasjonene. Eksisterende databasetester kjører uten endringer i migrasjonsfiler eller testoppsett.

Integrasjonsgruppen løfter Jackson 2 til `2.22.4` i begge konfigurasjonene og Jackson 3 til `3.2.4` i produksjon. Testene bruker fortsatt Jackson 3 `3.3.0`. De delte annotasjonene er `2.22` i produksjon og `2.23` i tester. Javalin, Kafka-klienten og alle fire tbd-libs-modulene er bekreftet på målversjonene. Ingen lyttere, klientkonfigurasjon eller testhjelpere måtte endres.

Tokenbibliotekene er bekreftet på `java-jwt:4.6.2` og `jwks-rsa:0.24.2` i begge konfigurasjonene. De gir ingen ytterligere Jackson-endring. Applikasjonens tokenvalidering, signering, nøkkelhåndtering og tilgangsregler er ikke endret.

### Endelig classpath

| Artefaktfamilie | Produksjon | Test |
|---|---|---|
| Jackson 2 BOM, core, databind, Kotlin-modul og JSR-310-modul | `2.22.4` | `2.22.4` |
| Jackson 3 BOM, core, databind og Kotlin-modul | `3.2.4` | `3.3.0` |
| Delte Jackson-annotasjoner | `2.22` | `2.23` |
| JUnit BOM, Jupiter og Platform Launcher | Ikke produksjonsavhengigheter | `6.1.3` |

Forskjellen i Jackson 3-versjon mellom produksjon og test skyldes testbiblioteket MockOAuth2Server. Forskjellen er dokumentert og ikke skjult med ekstra versjonsstyring. Testkjøringen bruker dermed ikke nøyaktig samme Jackson 3-versjon som produksjonsartefaktet. Kontrakttestene i iterasjon 3 må ta hensyn til dette.

Disse fire kommandoene er kjørt etter siste oppgradering, alle med exitkode 0 og uten uoppløste avhengigheter:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration runtimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration testRuntimeClasspath
```

Rårapportene er lagret lokalt med `--console=plain` i `build/reports/jackson-iteration-2/`: `runtimeClasspath.txt`, `testRuntimeClasspath.txt`, `runtimeClasspath-jackson.txt` og `testRuntimeClasspath-jackson.txt`. De slettes ved neste `clean`. Tabellene i dette dokumentet er det varige sammendraget.

## Kompatibilitetsforbehold

- [JUnit 6](https://github.com/junit-team/junit-framework/wiki/Upgrading-to-JUnit-6.0) krever Java 17 eller nyere og Kotlin 2.1 eller nyere. Prosjektets Java 25 og Kotlin `2.4.21` oppfyller disse minimumskravene. Eksisterende tester og lifecycle-oppsett beholdes.
- [MockOAuth2Server 7](https://github.com/navikt/mock-oauth2-server/blob/7.0.0/MIGRATION.md#migrating-to-700) har strengere regler for klientbinding ved authorization code, refresh og revocation. Dette er testserverens oppførsel, ikke en endring i applikasjonens auth-konfigurasjon. Eksisterende tester er grønne uten tilpasning, men det beviser ikke at alle mulige OAuth-flyter fungerer. Biblioteket gikk over til Jackson 3 allerede før prosjektets baseline `6.0.3`.
- [Kafka 4.4](https://kafka.apache.org/44/operations/java-version/) støtter Java 25. Nye asynkrone DNS-oppslag er opt-in og aktiveres ikke her (se [oppgraderingsguiden](https://kafka.apache.org/44/getting-started/upgrade/)). Vi fant ingen KafkaContainer-test i repoet, og ingen angitt broker-versjon som dokumenterer wire-kompatibilitet med produksjonen. Dette må kontrolleres mot plattformen ved senere deploy. Et grønt bygg alene dokumenterer ikke det.

## Endringskontroll

Vi har bare endret 14 eksisterende versjonsvariabler i `build.gradle.kts`. Det er ingen diff i `src/`, migrasjoner, wrapper, Java-oppsett, CI, Docker eller Nais. Ingen direkte avhengigheter er lagt til eller fjernet.

Hver gruppe er bygget og testet av koordinatoren før neste gruppe startet. Alle kjøringene har 206 tester i 24 testklasser uten feil eller hoppede tester. Tester og testforventninger er uendret. Eksisterende advarsler om utdaterte `asText`-kall, parameternavn og JVM class-data sharing er fortsatt til stede. Advarselskravene er ikke svekket.

Sluttkontrollen `./gradlew dependencyUpdates --console=plain` fullførte med exitkode 0 på 44 sekunder. De gjenværende oppgraderingsforslagene gjelder den utsatte Jackson-oppgraderingen og prerelease-versjoner som er avvist. Det er ingen gjenstående stabil oppgraderingskandidat blant de øvrige direkte avhengighetene i rapporten. Den lokale rapporten ligger i `build/dependencyUpdates/report.txt`.

## Rollback og kontroll etter deploy

Ved en regresjon settes de berørte versjonsvariablene i `build.gradle.kts` tilbake til verdiene i kolonnen «Fra», og den avtalte byggkommandoen kjøres på nytt. Flyway-modulene rulles tilbake sammen. Det samme gjelder de fire tbd-libs-modulene. Pluginversjonene fra iterasjon 1 skal ikke rulles tilbake sammen med bibliotekene.

Ingen migrasjonsfiler eller databaseservere endres i denne iterasjonen. Flyway-verktøyet oppgraderes, men eksisterende migrasjoner beholdes. Vi har ikke lagt til ny lagring eller nye meldingsfelt.

Ingen deploy utføres som del av arbeidet. Ved senere deploy kontrolleres eksisterende driftsinformasjon for HTTP-feil, tokenvalideringsfeil uten tokeninnhold, meldingsbehandling, consumer-lag, databasefeil og varselbestilling. Eksisterende logger og metrikker brukes. Vi legger ikke til payloadlogging eller personopplysninger i logger.

Testene er lokal verifikasjon, ikke bevis på alle forhold i produksjonsmiljøet. Hvis en senere deploy feiler, rulles applikasjonen tilbake til forrige verifiserte artefakt etter teamets vanlige prosedyre.

## Gjennomgang

| Perspektiv | Vurdering og grunnlag |
|---|---|
| Sikkerhet | Ingen endring i autentiseringskode, tilgang eller dataflyt. Eksisterende tilgangs- og tokentester er grønne etter versjonsoppgraderingene. Dette er ikke en uavhengig sikkerhetsrevisjon av bibliotekene. |
| Plattform | Bygg og eksisterende Docker-baserte databasetester fungerer. Ingen endring i servere, CI eller Nais. Kafka-kompatibilitet mot produksjon må verifiseres ved deploy. |
| Arkitektur | Kun eksisterende versjonspinner er endret. Bibliotekseide JSON-grenser beholdes. Ingen egen Jackson 3 BOM eller felles mapper er innført. |
| Endringssikkerhet | Fem separate grønne gruppebygg, uendrede testforventninger og gjennomgåtte avhengighetsgrafer. Produksjon og tester har forskjellig Jackson 3-versjon. Denne begrensningen tas videre til kontrakttestene. |
| Brukeropplevelse | Ingen endring i brukerflyten eller brukerrettet tekst. UI er ikke endret eller visuelt testet. Eksisterende API- og varseltester er grønne. |

Konklusjon: Iterasjon 2 er fullført med dokumenterte kompatibilitetsforbehold. Vi har ikke holdt tilbake noen biblioteksoppgraderinger utover den planlagte Jackson-migreringen. Det avtalte OpenTelemetry-unntaket er brukt. Iterasjon 3 er ikke startet. Ingen branch eller pull request er opprettet. Ingen deploy er utført.

🔴 Rød sone: ingen implementasjon i denne oppgaven. Endringer i autentiseringsregler eller forretningslogikk ville ha krevd separat avklaring. Slike endringer var ikke nødvendige.
