-- Access control + temporary contribution trees (separate from main).

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('admin', 'contributor')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_user_email_unique UNIQUE (email)
);

CREATE TABLE invite (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash           TEXT NOT NULL,
    email                TEXT NOT NULL,
    display_name         TEXT NOT NULL,
    role                 TEXT NOT NULL DEFAULT 'contributor'
                         CHECK (role IN ('admin', 'contributor')),
    status               TEXT NOT NULL DEFAULT 'pending'
                         CHECK (status IN ('pending', 'redeemed', 'revoked')),
    created_by_user_id   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    redeemed_by_user_id  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    redeemed_at          TIMESTAMPTZ,
    CONSTRAINT invite_token_hash_unique UNIQUE (token_hash)
);

CREATE TABLE app_session (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash   TEXT NOT NULL,
    user_id      UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_session_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX app_session_user_id_idx ON app_session (user_id);
CREATE INDEX invite_status_idx ON invite (status);

ALTER TABLE gedcom_tree
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'main'
        CHECK (kind IN ('main', 'contribution')),
    ADD COLUMN status TEXT
        CHECK (
            status IS NULL
            OR status IN ('draft', 'submitted', 'merged', 'expired', 'discarded')
        ),
    ADD COLUMN created_by_user_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN contributor_user_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN invite_id UUID REFERENCES invite (id) ON DELETE SET NULL,
    ADD COLUMN expires_at TIMESTAMPTZ;

-- Existing seeded tree becomes the durable main tree.
UPDATE gedcom_tree
SET kind = 'main',
    status = NULL
WHERE kind = 'main';

ALTER TABLE gedcom_tree
    ADD CONSTRAINT gedcom_tree_main_status_chk
    CHECK (
        (kind = 'main' AND status IS NULL)
        OR (kind = 'contribution' AND status IS NOT NULL)
    );

-- At most one active contribution tree per contributor.
CREATE UNIQUE INDEX gedcom_tree_one_active_contrib_per_user
    ON gedcom_tree (contributor_user_id)
    WHERE kind = 'contribution'
      AND contributor_user_id IS NOT NULL
      AND status IN ('draft', 'submitted');

CREATE INDEX gedcom_tree_kind_idx ON gedcom_tree (kind);
CREATE INDEX gedcom_tree_expires_at_idx ON gedcom_tree (expires_at)
    WHERE expires_at IS NOT NULL;
