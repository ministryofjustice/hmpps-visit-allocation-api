BEGIN;

ALTER TABLE negative_visit_order ADD COLUMN repaid_reason VARCHAR(20);

COMMIT;