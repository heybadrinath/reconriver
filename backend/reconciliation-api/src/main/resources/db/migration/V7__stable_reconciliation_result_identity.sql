ALTER TABLE reconciliation_work_item
    ADD COLUMN source_error_id UUID REFERENCES import_row_error(id) ON DELETE CASCADE;

ALTER TABLE reconciliation_result
    ADD COLUMN result_scope VARCHAR(24),
    ADD COLUMN work_key VARCHAR(260);

UPDATE reconciliation_result result
SET result_scope = work.work_type,
    work_key = work.work_key
FROM reconciliation_work_item work
WHERE work.id = result.work_item_id;

ALTER TABLE reconciliation_result
    ALTER COLUMN result_scope SET NOT NULL,
    ALTER COLUMN work_key SET NOT NULL,
    ADD CONSTRAINT ck_result_scope CHECK (result_scope IN ('ORDER', 'SETTLEMENT'));

CREATE INDEX idx_result_run_logical_identity
    ON reconciliation_result (run_id, result_scope, work_key);
