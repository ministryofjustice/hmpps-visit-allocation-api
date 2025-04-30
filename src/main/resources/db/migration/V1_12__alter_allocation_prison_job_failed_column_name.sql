-- Rename column failed_prisoners to failed_or_skipped_prisoners. If a prisoner has no changes, failed_or_skipped will contain this prisoner.
ALTER TABLE visit_order_allocation_prison_job
    RENAME COLUMN failed_prisoners TO failed_or_skipped_prisoners;
