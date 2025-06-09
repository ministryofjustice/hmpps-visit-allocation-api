ALTER TABLE change_log ADD COLUMN reference UUID;
UPDATE change_log SET reference = gen_random_uuid() WHERE reference IS NULL;
ALTER TABLE change_log ALTER COLUMN reference SET NOT NULL;