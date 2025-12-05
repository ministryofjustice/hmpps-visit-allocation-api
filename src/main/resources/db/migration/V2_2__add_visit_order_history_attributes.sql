CREATE TABLE visit_order_history_attributes
(
    id                      serial           NOT NULL PRIMARY KEY,
    visit_order_history_id  integer          NOT NULL,
    attribute_type          VARCHAR(40)      NOT NULL,
    attribute_value          VARCHAR(80)     NOT NULL,
    CONSTRAINT fk_attributes_to_visit_order_history  FOREIGN KEY (visit_order_history_id) REFERENCES visit_order_history(id)
);
