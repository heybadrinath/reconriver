ALTER TABLE import_file DROP CONSTRAINT uq_import_workspace_source_checksum;

CREATE UNIQUE INDEX uq_import_canonical_checksum
    ON import_file (workspace_id, source_type, checksum)
    WHERE status <> 'DUPLICATE';
