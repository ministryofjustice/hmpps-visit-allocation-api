BEGIN;

-- First clear the table, before running the migration script.
DELETE FROM visit_order_history_attributes;
DELETE FROM visit_order_history;

-- Run migration
INSERT INTO visit_order_history (
    prisoner_id,
    type,
    created_timestamp,
    vo_balance,
    pvo_balance,
    user_name,
    comment
)
SELECT
    pd.prisoner_id,
    'MIGRATION' AS type,
    NOW()       AS created_timestamp,
    COALESCE(vo_pos.count_vo, 0) - COALESCE(vo_neg.count_vo_neg, 0)   AS vo_balance,
    COALESCE(pvo_pos.count_pvo, 0) - COALESCE(pvo_neg.count_pvo_neg, 0) AS pvo_balance,
    'SYSTEM'    AS user_name,
    NULL::text  AS comment
FROM prisoner_details pd
         LEFT JOIN (
    -- positive VO balance: visit_order, type VO, AVAILABLE or ACCUMULATED
    SELECT
        prisoner_id,
        COUNT(*) AS count_vo
    FROM visit_order
    WHERE type = 'VO'
      AND status IN ('AVAILABLE', 'ACCUMULATED')
    GROUP BY prisoner_id
) vo_pos ON vo_pos.prisoner_id = pd.prisoner_id
         LEFT JOIN (
    -- negative VO balance: negative_visit_order, type VO, USED
    SELECT
        prisoner_id,
        COUNT(*) AS count_vo_neg
    FROM negative_visit_order
    WHERE type = 'VO'
      AND status = 'USED'
    GROUP BY prisoner_id
) vo_neg ON vo_neg.prisoner_id = pd.prisoner_id
         LEFT JOIN (
    -- positive PVO balance: visit_order, type PVO, AVAILABLE
    SELECT
        prisoner_id,
        COUNT(*) AS count_pvo
    FROM visit_order
    WHERE type = 'PVO'
      AND status = 'AVAILABLE'
    GROUP BY prisoner_id
) pvo_pos ON pvo_pos.prisoner_id = pd.prisoner_id
         LEFT JOIN (
    -- negative PVO balance: negative_visit_order, type PVO, USED
    SELECT
        prisoner_id,
        COUNT(*) AS count_pvo_neg
    FROM negative_visit_order
    WHERE type = 'PVO'
      AND status = 'USED'
    GROUP BY prisoner_id
) pvo_neg ON pvo_neg.prisoner_id = pd.prisoner_id;

COMMIT;