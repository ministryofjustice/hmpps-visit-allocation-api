BEGIN;

-- Add the columns as NULLABLE (no NOT NULL, no DEFAULT)
ALTER TABLE change_log ADD COLUMN visit_order_balance integer;
ALTER TABLE change_log ADD COLUMN privileged_visit_order_balance integer;

-- Back fill existing rows with 0 to avoid needing full migration in dev (prod empty)
UPDATE change_log
SET visit_order_balance = 0,
    privileged_visit_order_balance = 0;

-- Make the columns NOT NULL (since all rows now have a value)
ALTER TABLE change_log ALTER COLUMN visit_order_balance SET NOT NULL;
ALTER TABLE change_log ALTER COLUMN privileged_visit_order_balance SET NOT NULL;

COMMIT;