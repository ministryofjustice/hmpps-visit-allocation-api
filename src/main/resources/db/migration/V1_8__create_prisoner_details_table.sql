CREATE TABLE prisoner_details
(
    prisoner_id             VARCHAR(80)     NOT NULL UNIQUE PRIMARY KEY,
    last_allocated_date     DATE            NOT NULL
);
