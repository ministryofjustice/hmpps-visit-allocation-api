CREATE TABLE visit_order
(
    id                      serial          NOT NULL PRIMARY KEY,
    prisoner_id             VARCHAR(80)     NOT NULL,
    created_by_type         VARCHAR(20)     NOT NULL,
    created_by              VARCHAR(80)     NOT NULL,
    type                    VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    staff_comment           TEXT            NOT NULL,
    created_date            DATE            default current_date,
    expiry_date             DATE,
    outcome_reason_code     VARCHAR(20),
    mailed_date             DATE
);
