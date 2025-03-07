CREATE TABLE change_log
(
    id                      serial          NOT NULL PRIMARY KEY,
    prisoner_id             VARCHAR(80)     NOT NULL,
    change_timestamp        timestamp       NOT NULL,
    change_type             VARCHAR(30)     NOT NULL,
    change_source           VARCHAR(30)     NOT NULL,
    user_id                 VARCHAR(150)    NOT NULL,
    comment                 TEXT
);
