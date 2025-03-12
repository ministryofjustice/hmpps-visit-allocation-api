CREATE TABLE prisoner_details
(
    prisoner_id                VARCHAR(80)     NOT NULL PRIMARY KEY,
    last_vo_allocated_date     DATE            NOT NULL,
    last_pvo_allocated_date    DATE
);
