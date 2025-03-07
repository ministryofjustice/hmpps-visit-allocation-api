CREATE TABLE negative_visit_order
(
    id                      serial          NOT NULL PRIMARY KEY,
    prisoner_id             VARCHAR(80)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    type                    VARCHAR(20)     NOT NULL,
    created_date            DATE            default current_date,
    repaid_date             DATE
);

CREATE INDEX idx_negative_visit_order_composite
    ON negative_visit_order (prisoner_id, status);