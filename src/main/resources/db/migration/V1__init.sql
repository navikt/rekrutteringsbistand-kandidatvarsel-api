create table minside_varsel (
    dbid bigserial primary key,
    varsel_id text not null, -- id hos minside-varsel
    stilling_id text not null,
    opprettet timestamp without time zone not null,
    mal text not null,
    bestilt boolean not null,
    mottaker_fnr text not null,
    avsender_navident text not null,
    minside_status text,
    ekstern_status text,
    ekstern_kanal text,
    ekstern_feilmelding text
);

create index minside_varsel_mottaker_fnr_index on minside_varsel (mottaker_fnr);
create index minside_varsel_stilling_id_index on minside_varsel (stilling_id);

create table altinn_varsel (
    dbid bigserial primary key,
    frontend_id text not null unique,
    opprettet timestamp without time zone not null,
    mottaker_fnr text not null,
    avsender_navident text not null,
    stilling_id text not null,
    melding text not null,
    status text not null,
    status_endret text not null
);

create index altinn_varsel_mottaker_fnr_index on altinn_varsel (mottaker_fnr);
create index altinn_varsel_stilling_id_index on altinn_varsel (stilling_id);
