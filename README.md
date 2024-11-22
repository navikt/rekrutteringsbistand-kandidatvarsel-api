# rekrutteringsbistand-kandidatvarsel-api

For at rekrutteringsbistand skal kunne sende beskjeder til kandidater.

## Fakedings-autentisering (lokal utikling)
Vi bruker [fakedings](https://github.com/navikt/fakedings?tab=readme-ov-file) for autentisering
i lokal utvikling. For å hente et fake token, kan du gjøre et kall ala
```http request
POST /fake/aad
Host: fakedings.intern.dev.nav.no
Content-Type: application/x-www-form-urlencoded

client_id=someclientid&
aud=dev-gcp:toi:rekrutteringsbistand-kandidatvarsel-api&
acr=1&
pid=12345678910&
NAVident=1234
```
for eksempel med kommandoen curl:
```sh
curl --data 'client_id=someclientid&aud=dev-gcp:toi:rekrutteringsbistand-kandidatvarsel-api&acr=1&pid=12345678910&NAVident=1234' https://fakedings.intern.dev.nav.no/fake/aad
```
og så legge på svaret som 
``` 
Authorization: Bearer <token>
```


## Henvendelser

### For Nav-ansatte
* Dette Git-repositoriet eies av [Team Toi i Produktområde arbeidsgiver](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav
* IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
