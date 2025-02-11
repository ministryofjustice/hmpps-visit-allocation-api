CREATE TABLE visit_order_allocation_prison_job
(
    id                              serial          NOT NULL PRIMARY KEY,
    allocation_job_reference        text NOT NULL ,
    prison_code                     varchar(3) NOT NULL ,
    create_timestamp                timestamp       NOT NULL,
    start_timestamp                 timestamp,
    end_timestamp                   timestamp
);