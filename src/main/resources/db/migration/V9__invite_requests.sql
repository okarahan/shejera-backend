-- Public invite requests: contributor asks for an invite; admin approves → email.

CREATE TABLE invite_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    resolved_by_user_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    invite_id       UUID REFERENCES invite (id) ON DELETE SET NULL
);

CREATE INDEX invite_request_status_idx ON invite_request (status);

-- At most one open request per email (case-insensitive).
CREATE UNIQUE INDEX invite_request_one_pending_per_email
    ON invite_request (lower(email))
    WHERE status = 'pending';
