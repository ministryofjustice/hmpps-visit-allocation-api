BEGIN;

ALTER TABLE negative_visit_order ADD COLUMN repaid_reason VARCHAR(50);

COMMIT;