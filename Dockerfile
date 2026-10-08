ARG BASE_IMAGE_DIGEST_PINNED_REF
FROM ${BASE_IMAGE_DIGEST_PINNED_REF}

ENV TZ="Europe/Oslo"

ADD build/distributions/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT.tar /

ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-cp", "/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.kandidatvarsel.MainKt"]

EXPOSE 8080
