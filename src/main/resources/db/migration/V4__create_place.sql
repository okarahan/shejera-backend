CREATE TABLE place (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    latitude    NUMERIC(9, 6),
    longitude   NUMERIC(9, 6),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_place_name ON place (name);
