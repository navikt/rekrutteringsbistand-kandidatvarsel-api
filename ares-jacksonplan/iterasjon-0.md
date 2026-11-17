# Iterasjon 0: baseline før Jackson-migreringen

## Status

Fullført 17. november 2026 på branchen `start-jackson-migrering`, med utgangspunkt i commit `b9fbb46aea3767bbd7f1ae7c990ffdd4bd454bff`. Ingen kode, avhengigheter eller versjoner er endret i iterasjonen.

Arbeidskopien hadde fra før endringer i `ares-jacksonplan/plan.md` og en usporet `cplt-docker-tests.txt`. Disse er ikke rørt. Byggkommandoen nedenfor følger den eksisterende arbeidskopien av planen.

## Miljø og reproduksjon

| Verktøy | Verifisert versjon |
|---|---|
| Gradle Wrapper | 9.9.1 |
| Launcher- og daemon-JVM | Eclipse Adoptium 25.0.5+8-LTS |
| Prosjektets Kotlin-plugin | 2.4.20 |
| Gradles innebygde Kotlin | 2.4.21 |
| Docker Server | 29.10.0, rootless Docker |

Alle seks kommandoene fra planen fullførte med exitkode 0:

```bash
./gradlew --version
./gradlew clean build --warning-mode fail --configuration-cache -Dorg.gradle.configuration-cache.parallel=true
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration runtimeClasspath
./gradlew dependencyInsight --dependency jackson --configuration testRuntimeClasspath
```

Byggkommandoen ble kjørt to ganger, på henholdsvis 30 og 25 sekunder. Begge kjøringene var grønne. Alle ti aktive oppgaver ble kjørt hver gang, inkludert kompilering, tester og distribusjonsarkiver. Gradle gjenbrukte configuration cache. Dette dokumenterer rene bygg av prosjektet, ikke kjøringer med tom avhengighets- eller configuration cache.

Kjør kommandoene fra repoets rot med Java 25, tilgang til pakkeregistrene og fungerende Docker. Resultatet er et øyeblikksbilde av de oppløste avhengighetene, ikke dependency locking eller kontroll av nyeste tilgjengelige versjoner.

## Testresultat

XML-rapportene fra JUnit i `build/test-results/test/TEST-*.xml` viser:

| Måling | Antall |
|---|---|
| Testklasser | 24 |
| Tester | 206 |
| Feil (failures og errors) | 0 |
| Tester hoppet over | 0 |

Testcontainers startet Ryuk `0.14.0` og PostgreSQL med image-taggene `15-alpine`, `15` og `18`. Databasetestene kjørte mot Docker-containere. Verifikasjonen er dermed ikke begrenset til kontakt med Docker-daemonen.

Den lokale HTML-rapporten ligger i `build/reports/tests/test/index.html`. Rapportene under `build/` er genererte og forsvinner ved neste `clean`. Tallene over registrerer testresultatet uten å sjekke inn testlogger eller payloads.

## Avhengighetsgrunnlag

Fullstendige Gradle-rapporter er lagret separat:

- [Produksjonsavhengigheter](iterasjon-0/runtimeClasspath.txt)
- [Testavhengigheter](iterasjon-0/testRuntimeClasspath.txt)
- [Jackson i produksjon](iterasjon-0/jackson-runtimeClasspath.txt)
- [Jackson i tester](iterasjon-0/jackson-testRuntimeClasspath.txt)

Rapportene viser både forespurte og valgte versjoner, transitive stier og constraints. De er grunnlaget for avhengighetsdiff i neste iterasjon.

### Oppløste Jackson-versjoner

| Artefakt | runtimeClasspath | testRuntimeClasspath |
|---|---|---|
| `com.fasterxml.jackson:jackson-bom` | 2.22.3 | 2.22.3 |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.3 | 2.22.3 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.3 | 2.22.3 |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | 2.22.3 | 2.22.3 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.22.3 | 2.22.3 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | 2.22 |
| `tools.jackson:jackson-bom` | 3.2.2 | 3.2.2 |
| `tools.jackson.core:jackson-core` | 3.2.2 | 3.2.2 |
| `tools.jackson.core:jackson-databind` | 3.2.2 | 3.2.2 |
| `tools.jackson.module:jackson-module-kotlin` | 3.2.2 | 3.2.2 |

`jackson-annotations` deles av Jackson 2 og 3. Pakkenavnet alene beviser derfor ikke gjenværende Jackson 2-databinding.

### Biblioteker som trekker inn Jackson

| Kilde | Grense eller avhengighet |
|---|---|
| Egne direkte Jackson-avhengigheter | Jackson 2 BOM, Kotlin-modul og JSR-310-modul, alle 2.22.3 |
| `java-jwt:4.6.1` | Ber om Jackson 2 core/databind 2.22.2; Gradle velger 2.22.3 |
| `jwks-rsa:0.24.1` | Ber om Jackson 2 databind 2.21.2; Gradle velger 2.22.3 |
| `kotlin-builder:2.2.0` | Ber om Jackson 2 Kotlin- og JSR-310-moduler 2.20.1; Gradle velger 2.22.3 |
| `logstash-logback-encoder:9.0` | Ber om Jackson 3 databind 3.0.1; Gradle velger 3.2.2 |
| tbd-libs `20260917.2152` | Ber om Jackson 2 BOM 2.22.1 og Jackson 3 BOM 3.2.1 og 3.2.2; Gradle velger henholdsvis 2.22.3 og 3.2.2, med Jackson 3 Kotlin-modul 3.2.2 |
| `mock-oauth2-server:6.0.3` (test) | Jackson 3 databind og Kotlin-modul 3.2.2 |
| `rapids-and-rivers-test:20260917.2152` (test) | Begge BOM-familier og Jackson 3 Kotlin-modul |
| Flyway, TMS varsel-action og docker-java (test) | Felles Jackson-annotasjoner; ikke i seg selv en Jackson 2-mapper |

Javalins mapper er en separat integrasjonsgrense i applikasjonen, selv om Javalin ikke deklarerer Jackson som en transitiv avhengighet i runtime-rapporten.

En eksisterende forskjell mellom produksjon og tester er `kotlin-reflect`: runtime velger `2.1.21`, mens test-runtime velger `2.2.21`. Jackson-versjonene er like i de to konfigurasjonene. Forskjellen er registrert, ikke endret.

## Mapper-konfigurasjon og JSON-grenser

Filreferanser i denne delen er relative til `src/main/kotlin/no/nav/toi/kandidatvarsel/`, med mindre annet er angitt.

| Brukssted | Eier og eksisterende oppførsel |
|---|---|
| `Javalin.kt:24–30` | Egen Jackson 2-mapper via `JavalinJackson`, med Kotlin-modul, `JavaTimeModule` og `WRITE_DATES_AS_TIMESTAMPS` deaktivert. Brukes av blant annet `ctx.json` og `bodyAsClass` i `VarslerApi.kt:95–107`. |
| `HttpKlient.kt:27–28,61–65,90–94` | Separat Jackson 2-mapper for tokenrespons. `FAIL_ON_UNKNOWN_PROPERTIES` er deaktivert. `TokenResponse` har også `@JsonIgnoreProperties(ignoreUnknown = true)`. Tokenhåndteringen er bare kartlagt, ikke endret. |
| `kandidatsøk/KandidatsokApiKlient.kt:64–72` | Oppretter en lokal Jackson 2-mapper for hver request-body. Ingen egne dato- eller nullinnstillinger. |
| `minside/MinsideClient.kt:187–213` | Egen Jackson 2-mapper leser oppdateringer. DTO-en ignorerer ukjente felt, mapper `@event_name` eksplisitt og har nullable `status`, `kanal` og `feilmelding` med `null` som standard. |
| `minside/MinsideServices.kt:14,94–112` | Egen Jackson 2-mapper med `JavaTimeModule`, uten eksplisitt deaktivering av timestamps. `opprettet` formateres til `ISO_OFFSET_DATE_TIME`-streng før serialisering til Rapid. |
| `minside/MinsideVarsel.kt:17–26,77–84,288–306` | Egen Jackson 2-mapper skriver `flettedata` som liste med strenger i JSONB via `PGobject`. Leser med `typeFactory` som `List<String>`. |
| `minside/MinsideClient.kt:31–56,109–135` | TMS `VarselActionBuilder.opprett` eier serialiseringen av bestillinger. Appen sender den ferdige JSON-strengen på Kafka. |
| `rapids/lyttere/` | Rapids eier innlesing og `JsonMessage`-operasjoner. Fire lyttere bruker Jackson 3-typen `JsonNode`: `KandidatCvDeltMedArbeidsgiverBehovLytter.kt:12`, `KandidatCvDeltMedArbeidsgiverLytter.kt:14`, `KandidatLukketKandidatlisteBehovLytter.kt:11` og `KandidatLukketKandidatlisteLytter.kt:13`. Publisering bruker blant annet `packet.toJson()`. |
| Logstash Encoder | Bibliotekseid Jackson 3-grense for loggformatet. Den oppløste avhengigheten er registrert over. Dette er ikke applikasjonens HTTP- eller JSONB-mapper. |

Mapperne har ingen eksplisitt `JsonInclude`-innstilling. Nullfelt følger derfor den aktuelle mapperens eller bibliotekets standarder. Ingen felles applikasjonsmapper er definert. Disse forskjellene må bevares ved et senere mapperbytte.

### Eksisterende testdekning

Filreferanser nedenfor er relative til `src/test/kotlin/no/nav/toi/kandidatvarsel/`.

- `minside/MinsideBestillingJsonTest.kt:15–125` låser sentrale feltnavn og verdier i TMS-bestillinger, inkludert varighet som datostreng og ekstern varsling. `minside/StillingsannonseOppdatertTest.kt:9–42` sjekker tekst og lenke.
- `VarselOppdateringClientTest.kt:127–274` bruker hendelser med ekstra felt. Testene dekker dermed eksisterende toleranse for ukjente felt i denne grensen.
- `RekrutteringstreffRapidTest.kt:56–124,207–242` sjekker Rapid-utdata. Testen med `flettedata` dekker skriving, lesing og publisering av listen gjennom databasen.
- Lyttertestene i `rapids/lyttere/` dekker blant annet manglende felt, ugyldige UUID-er og tidspunkter, array/object-form og behovs-/replay-flyter.
- `kandidatsøk/KandidatsokApiKlientTest.kt:43–55` sammenligner request-body strukturelt, inkludert eksplisitte `null`-verdier i `aktorid` og `kandidatnr`.
- `AzureTokenClientTest.kt:29–45` bruker tokenrespons med ekstra felt. `HttpKlientTest.kt:13–50` tester tidsavbrudd, ikke mapperoppførsel.
- `VarslerApiTest.kt:202–228` sjekker nullable statusfelt som JSON `null`. `VarslerEndepunktTest.kt:59–96` dekker responsfelter og statusverdier.
- `util/TestRapid.kt:10–16,81–120` og `FakeMinside.kt:13–37,80–95` bruker Jackson 2 til å lese eller lage testmeldinger. Rapids-testbiblioteket bruker samtidig Jackson 3. Flere testforventninger deler dermed teknologi med produksjonskoden.

Kartleggingen fant ikke en egen historisk JSONB-fixture, direkte tester av oppførsel for ukjente felt og null i Javalins `bodyAsClass`, eller en test som låser `LocalDateTime`-format i HTTP-respons. Grønn baseline beviser ikke disse kontraktene. Slike tester hører til iterasjon 3, før mapperbytte.

## Eksisterende advarsler og blokkeringer

Ingen bygg- eller testfeil og ingen gjenstående sandbox-blokkeringer ble observert i denne kjøringen.

- Kotlin-kompilatoren rapporterte 13 deprecated `asText`-kall i `KandidatInvitertLytter.kt`, `KandidatInvitertTreffEndretLytter.kt` og `KandidatTreffAvlystLytter.kt`.
- Testkompileringen advarte om at en overstyrt parameter heter noe annet enn `stillingId` i `KandidatLukketKandidatlisteLytterTest.kt:107`.
- JVM-en advarte om begrenset class-data sharing fordi bootstrap-classpath var utvidet.
- Gradle opplyste at parallell configuration cache er en eksperimentell funksjon (incubating).

`--warning-mode fail` ga ikke byggfeil. Flagget gjør ikke Kotlin-kompilatorens advarsler til feil. Advarslene over tilhører baseline og skal ikke tilskrives senere oppgraderinger.

## Endringskontroll

`git diff --exit-code -- build.gradle.kts settings.gradle.kts gradle.properties gradle src .github/workflows` fullførte uten diff. Iterasjonen endrer ikke kjørbar kode, autentisering, JSON-kontrakter, databaseskjema, CI eller Nais-konfigurasjon.

Ingen deploy er nødvendig. Rollback av denne iterasjonen innebærer å ta ut baseline-dokumentasjonen; applikasjonsartefaktet er uendret. Eksisterende målinger for HTTP-feil, meldingsbehandling, JSONB og varselbestilling endres ikke. Nye logger eller metrikker er ikke lagt til.

| Perspektiv | Vurdering og grunnlag |
|---|---|
| Sikkerhet | Ingen endring i auth, tilgang eller dataflyt. Testlogger og payloads er ikke inkludert i baseline-filene. |
| Plattform | Bygg og Docker-baserte tester fungerer i cplt. Ingen endring i CI, ressurser eller Nais. |
| Arkitektur | Eksisterende mappergrenser og bibliotekseierskap er kartlagt, uten ny felles mapper eller migrering. |
| Endringssikkerhet | To grønne rene bygg, registrerte avhengighetsgrafer og ingen diff i kode eller byggkonfigurasjon. Dekningsgap er skilt fra testfeil. |
| Brukeropplevelse | Ingen endring i brukerflyten. Dokumentasjonens kommandoer er kjørt, og lenkede avhengighetsrapporter finnes. Ingen UI er endret eller visuelt testet. |

Konklusjon: Iterasjon 0 er fullført. Iterasjon 1 er ikke startet.

🔴 Rød sone: ingen for denne oppgaven.
