CREATE TABLE prisoner_details
(
    id                      serial          NOT NULL PRIMARY KEY,
    prisoner_id             VARCHAR(80)     NOT NULL UNIQUE,
    last_allocated_date     DATE            NOT NULL
);

CREATE INDEX idx_prisoner_details_prisoner_id ON prisoner_details (prisoner_id);

