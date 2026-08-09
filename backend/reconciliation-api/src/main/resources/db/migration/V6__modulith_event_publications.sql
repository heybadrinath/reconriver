CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    publication_date TIMESTAMPTZ NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(255)
);

CREATE INDEX idx_event_publication_incomplete ON event_publication (publication_date)
    WHERE completion_date IS NULL;

CREATE TABLE event_publication_archive (
    id UUID PRIMARY KEY,
    publication_date TIMESTAMPTZ NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(255)
);
