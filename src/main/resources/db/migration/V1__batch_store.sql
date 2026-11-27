-- The batch store (solution design §13.1). The search reads it to hide hearings in flight and to
-- label hearings whose latest bulk attempt did not share. The tables stay empty until submit is
-- built; only the search's reads are implemented against them today.

CREATE TABLE bulk_resulting (
    bulk_id          UUID PRIMARY KEY,
    created_by       UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_hash     TEXT        NOT NULL,
    court_centre_id  UUID        NOT NULL,
    hearing_day      DATE        NOT NULL,
    filter           JSONB,
    template         JSONB       NOT NULL,
    attendance_type  TEXT,
    court_clerk      JSONB,
    aec_session      JSONB,
    status           TEXT        NOT NULL,
    hearing_count    INTEGER     NOT NULL DEFAULT 0,
    done_count       INTEGER     NOT NULL DEFAULT 0,
    failed_count     INTEGER     NOT NULL DEFAULT 0,
    not_taken_count  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT bulk_resulting_status_check
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

CREATE INDEX bulk_resulting_court_created_idx ON bulk_resulting (court_centre_id, created_at);

CREATE TABLE bulk_resulting_hearing (
    bulk_id                 UUID        NOT NULL REFERENCES bulk_resulting (bulk_id),
    hearing_id              UUID        NOT NULL,
    case_ids                UUID[]      NOT NULL DEFAULT '{}',
    taken_by_bulk_id        UUID,
    booking_id              UUID,
    booking_released_at     TIMESTAMPTZ,
    status                  TEXT        NOT NULL,
    step                    TEXT,
    version_read            INTEGER,
    attempt                 INTEGER     NOT NULL DEFAULT 0,
    current_correlation_id  UUID,
    pending_defendant_id    UUID,
    row_version             INTEGER     NOT NULL DEFAULT 0,
    lease_owner             TEXT,
    lease_until             TIMESTAMPTZ,
    failure_code            TEXT,
    failure_detail          TEXT,
    shared_by               UUID,
    shared_at               TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (bulk_id, hearing_id),
    CONSTRAINT bulk_resulting_hearing_status_check
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'UNCONFIRMED', 'SUCCESS', 'SHARED_ELSEWHERE',
                          'NOT_ELIGIBLE', 'DRAFT_IN_PROGRESS', 'CONFLICT', 'SLOTS_UNAVAILABLE',
                          'TECHNICAL', 'NOT_SHARED', 'NOT_TAKEN')),
    CONSTRAINT bulk_resulting_hearing_step_check
        CHECK (step IS NULL OR step IN ('READ', 'DRAFT_SENT', 'DRAFT_SAVED', 'ATTENDANCE_SENT',
                                        'SHARE_SENT')),
    CONSTRAINT bulk_resulting_hearing_taken_by_check
        CHECK (taken_by_bulk_id IS NULL OR status = 'NOT_TAKEN')
);

-- A hearing can be in flight in one batch only (§8.5).
CREATE UNIQUE INDEX bulk_resulting_hearing_in_flight_uidx
    ON bulk_resulting_hearing (hearing_id)
    WHERE status IN ('PENDING', 'IN_PROGRESS', 'UNCONFIRMED');

CREATE INDEX bulk_resulting_hearing_bulk_status_idx ON bulk_resulting_hearing (bulk_id, status);

-- The latest outcome per hearing, which drives the "Failed to share" label.
CREATE INDEX bulk_resulting_hearing_latest_outcome_idx
    ON bulk_resulting_hearing (hearing_id, updated_at)
    WHERE status <> 'NOT_TAKEN';

-- The deployment-wide in-flight tokens (§5): a worker claims a free or expired token before it
-- starts a hearing and releases it when the hearing reaches a final status or UNCONFIRMED.
CREATE TABLE bulk_inflight (
    token_id     SMALLINT PRIMARY KEY,
    owner        TEXT,
    hearing_id   UUID,
    lease_until  TIMESTAMPTZ
);

INSERT INTO bulk_inflight (token_id)
SELECT generate_series(1, 10);
