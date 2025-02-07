-- Create an index on the type column
CREATE INDEX idx_visit_order_type ON visit_order(type);

-- Create an index on the status column
CREATE INDEX idx_visit_order_status ON visit_order(status);

CREATE INDEX idx_visit_order_created_date ON visit_order(created_date);

CREATE INDEX idx_visit_order_prisoner_id ON visit_order(prisoner_id);

