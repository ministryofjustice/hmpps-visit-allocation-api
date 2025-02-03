CREATE TABLE visit_order_prison
(
    id   serial      NOT NULL PRIMARY KEY,
    prison_code varchar(3)  UNIQUE NOT NULL,
    active boolean   NOT NULL
);
