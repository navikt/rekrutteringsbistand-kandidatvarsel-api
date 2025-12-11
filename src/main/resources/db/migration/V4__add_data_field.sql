-- Legger til flettedata-felt for å lagre strukturerte data (JSON) som skal flettes inn i meldingstekster.
ALTER TABLE minside_varsel ADD COLUMN flettedata jsonb;
