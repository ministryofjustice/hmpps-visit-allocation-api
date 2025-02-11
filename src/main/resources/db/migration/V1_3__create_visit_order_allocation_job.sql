CREATE TABLE visit_order_allocation_job
(
    id                      serial          NOT NULL PRIMARY KEY,
    reference               text            UNIQUE,
    create_timestamp        timestamp       NOT NULL,
    total_prisons           int             NOT NULL
);
