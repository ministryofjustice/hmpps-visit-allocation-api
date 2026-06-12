UPDATE visit_order_history_attributes
SET attribute_type = CASE attribute_type
    WHEN '0' THEN 'VISIT_REFERENCE'
    WHEN '1' THEN 'INCENTIVE_LEVEL'
    WHEN '2' THEN 'OLD_PRISONER_ID'
    WHEN '3' THEN 'NEW_PRISONER_ID'
    WHEN '4' THEN 'ADJUSTMENT_REASON_TYPE'
    ELSE attribute_type
END
WHERE attribute_type IN ('0', '1', '2', '3', '4');
