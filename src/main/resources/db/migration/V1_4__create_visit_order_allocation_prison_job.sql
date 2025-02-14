CREATE TABLE visit_order_allocation_prison_job
(
    id                              serial          NOT NULL PRIMARY KEY,
    allocation_job_reference        text NOT NULL ,
    prison_code                     varchar(3) NOT NULL ,
    create_timestamp                timestamp       NOT NULL,
    start_timestamp                 timestamp,
    end_timestamp                   timestamp,
    failure_message                 VARCHAR(100),
    convicted_prisoners             int,
    processed_prisoners             int,
    failed_prisoners                int
);

CREATE INDEX visit_order_allocation_prison_job_ref_prison_code ON visit_order_allocation_prison_job(allocation_job_reference, prison_code);
