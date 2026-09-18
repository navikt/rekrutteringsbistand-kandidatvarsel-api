FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
ENV TZ="Europe/Oslo"
ADD build/distributions/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT.tar /
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-cp", "/rekrutteringsbistand-kandidatvarsel-api-1.0-SNAPSHOT/lib/*", "no.nav.toi.kandidatvarsel.MainKt"]
