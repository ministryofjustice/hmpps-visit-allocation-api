CREATE TABLE visit_order_history
(
    id                      serial           NOT NULL PRIMARY KEY,
    prisoner_id             VARCHAR(80)      NOT NULL,
    type                    VARCHAR(40)      NOT NULL,
    created_timestamp       timestamp        NOT NULL default current_timestamp,
    vo_balance              integer          NOT NULL,
    pvo_balance             integer          NOT NULL,
    user_name                VARCHAR(50)      NOT NULL,
    comment                 text
);

CREATE INDEX idx_visit_order_history_prisoner_id ON visit_order_history (prisoner_id);
