ALTER TABLE visit_order_allocation_prison_job ADD failure_message VARCHAR(100);
ALTER TABLE visit_order_allocation_prison_job ADD convicted_prisoners int;
ALTER TABLE visit_order_allocation_prison_job ADD processed_prisoners int;
ALTER TABLE visit_order_allocation_prison_job ADD failed_prisoners int;
