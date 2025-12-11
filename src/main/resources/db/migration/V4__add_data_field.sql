-- Legger til flettedata-felt for å lagre strukturerte data (JSON) som skal flettes inn i meldingstekster
-- Dette matcher tilsvarende hendelse_data felt i jobbsoker_hendelse tabellen i rekrutteringstreff-api
ALTER TABLE minside_varsel ADD COLUMN flettedata jsonb;
