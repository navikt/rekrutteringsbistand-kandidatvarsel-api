create type stilling_varsel_status as enum (
    'UNDER_UTSENDING',
    'SENDT',
    'FEIL'
);

create table stilling_varsel (
    id bigserial primary key,
    id_for_frontend text not null,
    opprettet timestamp not null,
    status stilling_varsel_status not null,
    status_endret timestamp not null,
    fnr varchar(11) not null,
    stilling_id text not null,
    navident varchar(7) not null
);

create table altinn_sms (
    id bigserial primary key,
    stilling_varsel_id bigint not null references stilling_varsel(id),
    sms_tekst text not null
);

create table minside_varsel (
    id bigserial primary key,
    minside_varsel_id uuid not null,
    status text not null
);