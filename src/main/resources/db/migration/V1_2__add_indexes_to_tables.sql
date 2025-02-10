CREATE INDEX idx_visit_order_composite
    ON visit_order (prisoner_id, type, status, created_date);

CREATE INDEX idx_visit_order_last_allocated
    ON visit_order (prisoner_id, type, created_date DESC);

CREATE INDEX idx_visit_order_id ON visit_order (id);