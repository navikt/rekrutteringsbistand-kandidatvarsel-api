FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT.tar /
ENTRYPOINT ["java", "-cp", "/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.kandidatvarsel.MainKt"]