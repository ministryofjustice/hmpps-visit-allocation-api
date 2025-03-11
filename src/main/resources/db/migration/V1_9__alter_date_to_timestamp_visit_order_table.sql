BEGIN;

-- Drop existing indexes that reference created_date
DROP INDEX IF EXISTS idx_visit_order_composite;
DROP INDEX IF EXISTS idx_visit_order_last_allocated;

-- Rename and modify the column
ALTER TABLE visit_order
    RENAME COLUMN created_date TO created_timestamp;

ALTER TABLE visit_order
ALTER COLUMN created_timestamp TYPE TIMESTAMP USING created_timestamp::TIMESTAMP,
ALTER COLUMN created_timestamp SET DEFAULT current_timestamp,
ALTER COLUMN created_timestamp SET NOT NULL;

-- Recreate indexes with the updated column name
CREATE INDEX idx_visit_order_composite
    ON visit_order (prisoner_id, type, status, created_timestamp);

CREATE INDEX idx_visit_order_last_allocated
    ON visit_order (prisoner_id, type, created_timestamp DESC);

COMMIT;