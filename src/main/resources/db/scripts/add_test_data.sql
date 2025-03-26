BEGIN;

INSERT INTO prisoner_details (prisoner_id, last_vo_allocated_date, last_pvo_allocated_date)
VALUES ('G2780GX', '2025-03-25', '2025-03-25');

INSERT INTO visit_order (prisoner_id, type, status, created_timestamp, expiry_date)
VALUES ('G2780GX', 'VO', 'AVAILABLE', '2025-03-25T10:30:00', null);

INSERT INTO negative_visit_order (prisoner_id, type, status, created_timestamp, repaid_date)
VALUES ('G2780GX', 'NEGATIVE_VO', 'REPAID', '2025-03-25T10:30:00', '2025-03-25');

COMMIT;