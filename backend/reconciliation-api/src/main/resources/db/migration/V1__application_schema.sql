CREATE TABLE demo_workspace (
    id UUID PRIMARY KEY,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_workspace_expiry ON demo_workspace (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE demo_session_token (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_session_workspace ON demo_session_token (workspace_id);

CREATE TABLE import_file (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    source_type VARCHAR(24) NOT NULL CHECK (source_type IN ('INTERNAL', 'PROCESSOR', 'BANK')),
    sanitized_file_name VARCHAR(255) NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('VALIDATING', 'QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'DUPLICATE')),
    total_rows INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    imported_rows INTEGER NOT NULL DEFAULT 0 CHECK (imported_rows >= 0),
    quarantined_rows INTEGER NOT NULL DEFAULT 0 CHECK (quarantined_rows >= 0),
    duplicate_of_id UUID REFERENCES import_file(id),
    job_execution_id BIGINT,
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_workspace_source_checksum UNIQUE (workspace_id, source_type, checksum)
);

CREATE INDEX idx_import_workspace_created ON import_file (workspace_id, created_at DESC);

CREATE TABLE import_staging_row (
    id UUID PRIMARY KEY,
    import_file_id UUID NOT NULL REFERENCES import_file(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    source_type VARCHAR(24) NOT NULL,
    row_number INTEGER NOT NULL CHECK (row_number > 0),
    raw_row JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_staging_import_row UNIQUE (import_file_id, row_number)
);

CREATE INDEX idx_staging_pending ON import_staging_row (import_file_id, row_number) WHERE processed = FALSE;

CREATE TABLE import_row_error (
    id UUID PRIMARY KEY,
    import_file_id UUID NOT NULL REFERENCES import_file(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    message VARCHAR(500) NOT NULL,
    raw_row JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_import_error_row_code UNIQUE (import_file_id, row_number, error_code)
);

CREATE INDEX idx_import_error_workspace ON import_row_error (workspace_id, import_file_id, row_number);

CREATE TABLE internal_transaction (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    import_file_id UUID NOT NULL REFERENCES import_file(id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL,
    internal_payment_id VARCHAR(128) NOT NULL,
    merchant_order_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    synthetic_customer_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_internal_source_identifier UNIQUE (workspace_id, internal_payment_id, import_file_id, source_row_number),
    CONSTRAINT ck_internal_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_internal_money_scale CHECK (gross_amount = ROUND(gross_amount, 4))
);

CREATE INDEX idx_internal_workspace_order ON internal_transaction (workspace_id, merchant_order_id);
CREATE INDEX idx_internal_workspace_payment ON internal_transaction (workspace_id, internal_payment_id);

CREATE TABLE processor_transaction (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    import_file_id UUID NOT NULL REFERENCES import_file(id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL,
    processor_transaction_id VARCHAR(128) NOT NULL,
    merchant_order_id VARCHAR(128) NOT NULL,
    processor_event_type VARCHAR(32) NOT NULL,
    processor_event_time TIMESTAMPTZ NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    fee_amount NUMERIC(19,4) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    settlement_batch_id VARCHAR(128),
    processor_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processor_source_identifier UNIQUE (workspace_id, processor_transaction_id, import_file_id, source_row_number),
    CONSTRAINT ck_processor_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_processor_net CHECK (net_amount = gross_amount - fee_amount),
    CONSTRAINT ck_processor_money_scale CHECK (gross_amount = ROUND(gross_amount, 4) AND fee_amount = ROUND(fee_amount, 4) AND net_amount = ROUND(net_amount, 4))
);

CREATE INDEX idx_processor_workspace_order ON processor_transaction (workspace_id, merchant_order_id);
CREATE INDEX idx_processor_workspace_batch ON processor_transaction (workspace_id, settlement_batch_id);
CREATE INDEX idx_processor_workspace_identifier ON processor_transaction (workspace_id, processor_transaction_id);

CREATE TABLE settlement_batch (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    external_batch_id VARCHAR(128) NOT NULL,
    currency CHAR(3) NOT NULL,
    expected_credit NUMERIC(19,4) NOT NULL,
    first_event_at TIMESTAMPTZ NOT NULL,
    last_event_at TIMESTAMPTZ NOT NULL,
    transaction_count INTEGER NOT NULL CHECK (transaction_count > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_settlement_batch UNIQUE (workspace_id, external_batch_id, currency)
);

CREATE TABLE bank_settlement (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    import_file_id UUID NOT NULL REFERENCES import_file(id) ON DELETE CASCADE,
    source_row_number INTEGER NOT NULL,
    bank_entry_id VARCHAR(128) NOT NULL,
    settlement_batch_id VARCHAR(128) NOT NULL,
    booked_at TIMESTAMPTZ NOT NULL,
    credited_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    bank_reference VARCHAR(128) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bank_source_identifier UNIQUE (workspace_id, bank_entry_id, import_file_id, source_row_number),
    CONSTRAINT ck_bank_currency CHECK (currency = UPPER(currency)),
    CONSTRAINT ck_bank_money_scale CHECK (credited_amount = ROUND(credited_amount, 4))
);

CREATE INDEX idx_bank_workspace_batch ON bank_settlement (workspace_id, settlement_batch_id);
CREATE INDEX idx_bank_workspace_identifier ON bank_settlement (workspace_id, bank_entry_id);

CREATE TABLE reconciliation_run (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('QUEUED', 'STARTING', 'RUNNING', 'COMPLETED', 'FAILED')),
    total_work_items INTEGER NOT NULL DEFAULT 0 CHECK (total_work_items >= 0),
    processed_items INTEGER NOT NULL DEFAULT 0 CHECK (processed_items >= 0),
    matched_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_count >= 0),
    exception_count INTEGER NOT NULL DEFAULT 0 CHECK (exception_count >= 0),
    job_execution_id BIGINT,
    failure_message VARCHAR(1000),
    controlled_failure_after_chunks INTEGER,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_active_run_per_workspace ON reconciliation_run (workspace_id)
    WHERE status IN ('QUEUED', 'STARTING', 'RUNNING');
CREATE INDEX idx_run_workspace_created ON reconciliation_run (workspace_id, created_at DESC);

CREATE TABLE reconciliation_work_item (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    work_type VARCHAR(24) NOT NULL CHECK (work_type IN ('ORDER', 'SETTLEMENT')),
    work_key VARCHAR(260) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_run_work_key UNIQUE (run_id, work_type, work_key)
);

CREATE INDEX idx_run_work_pending ON reconciliation_work_item (run_id, work_type, work_key) WHERE processed = FALSE;

CREATE TABLE reconciliation_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    work_item_id UUID NOT NULL REFERENCES reconciliation_work_item(id) ON DELETE CASCADE,
    outcome VARCHAR(40) NOT NULL,
    rule_code VARCHAR(80) NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    internal_transaction_id UUID REFERENCES internal_transaction(id),
    processor_transaction_id UUID REFERENCES processor_transaction(id),
    settlement_batch_id UUID REFERENCES settlement_batch(id),
    bank_settlement_id UUID REFERENCES bank_settlement(id),
    internal_payment_reference VARCHAR(128),
    processor_transaction_reference VARCHAR(128),
    bank_entry_reference VARCHAR(128),
    gross_amount NUMERIC(19,4),
    processor_fee NUMERIC(19,4),
    net_amount NUMERIC(19,4),
    bank_credit NUMERIC(19,4),
    difference NUMERIC(19,4),
    currency CHAR(3),
    internal_occurred_at TIMESTAMPTZ,
    processor_event_at TIMESTAMPTZ,
    bank_booked_at TIMESTAMPTZ,
    resolution_status VARCHAR(24) NOT NULL CHECK (resolution_status IN ('NOT_REQUIRED', 'OPEN', 'RESOLVED', 'REOPENED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_work_item UNIQUE (run_id, work_item_id)
);

CREATE INDEX idx_result_workspace_outcome ON reconciliation_result (workspace_id, outcome, created_at DESC);
CREATE INDEX idx_result_run_outcome ON reconciliation_result (run_id, outcome);
CREATE INDEX idx_result_open_exception ON reconciliation_result (workspace_id, resolution_status, created_at DESC)
    WHERE resolution_status IN ('OPEN', 'REOPENED');

CREATE TABLE exception_resolution (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL REFERENCES reconciliation_result(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    action VARCHAR(16) NOT NULL CHECK (action IN ('RESOLVED', 'REOPENED')),
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resolution_result_created ON exception_resolution (result_id, created_at);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id) ON DELETE CASCADE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    details JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_workspace_time ON audit_event (workspace_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_aggregate ON audit_event (workspace_id, aggregate_type, aggregate_id, occurred_at);

CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER audit_event_no_delete BEFORE DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
