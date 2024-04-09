create table altinn_varsel (
    dbid bigserial primary key,
    frontend_id text not null unique,
    opprettet timestamp without time zone not null,
    mottaker_fnr text not null,
    avsender_navident text not null,
    stilling_id text not null,
    melding text not null,
    status text not null
);

alter table altinn_varsel
drop column sist_endret;