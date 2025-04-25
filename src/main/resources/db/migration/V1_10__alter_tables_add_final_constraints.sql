BEGIN;

-- Add foreign key constraint between visit_order and prisoner_details
ALTER TABLE visit_order
    ADD CONSTRAINT fk_visit_order_prisoner_id
        FOREIGN KEY (prisoner_id) REFERENCES prisoner_details (prisoner_id)
            ON DELETE CASCADE;

-- Add foreign key constraint between negative_visit_order and prisoner_details
ALTER TABLE negative_visit_order
    ADD CONSTRAINT fk_negative_visit_order_prisoner_id
        FOREIGN KEY (prisoner_id) REFERENCES prisoner_details (prisoner_id)
            ON DELETE CASCADE;

-- Add foreign key constraint between change_log and prisoner_details
ALTER TABLE change_log
    ADD CONSTRAINT fk_change_log_prisoner_id
        FOREIGN KEY (prisoner_id) REFERENCES prisoner_details (prisoner_id)
            ON DELETE CASCADE;

COMMIT;