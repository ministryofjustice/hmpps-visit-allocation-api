BEGIN;

ALTER TABLE change_log ADD COLUMN visit_order_accumulated_balance integer;
ALTER TABLE change_log ADD COLUMN visit_order_available_balance integer;
ALTER TABLE change_log ADD COLUMN visit_order_used_balance integer;

ALTER TABLE change_log ADD COLUMN privileged_visit_order_available_balance integer;
ALTER TABLE change_log ADD COLUMN privileged_visit_order_used_balance integer;

COMMIT;