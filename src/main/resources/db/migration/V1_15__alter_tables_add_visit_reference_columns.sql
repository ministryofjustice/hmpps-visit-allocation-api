BEGIN;

ALTER TABLE visit_order ADD COLUMN visit_reference text;
ALTER TABLE negative_visit_order ADD COLUMN visit_reference text;

COMMIT;