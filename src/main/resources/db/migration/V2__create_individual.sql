CREATE TABLE individual (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tree_id     UUID NOT NULL REFERENCES gedcom_tree (id) ON DELETE CASCADE,
    xref        TEXT NOT NULL,
    sex         TEXT CHECK (sex IN ('M', 'F', 'X', 'U')),
    is_living   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tree_id, xref)
);

CREATE INDEX idx_individual_tree_id ON individual (tree_id);

CREATE TABLE individual_name (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    individual_id   UUID NOT NULL REFERENCES individual (id) ON DELETE CASCADE,
    name_type       TEXT,
    name_full       TEXT,
    given_name      TEXT,
    surname         TEXT,
    is_preferred    BOOLEAN NOT NULL DEFAULT false,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_individual_name_individual_id ON individual_name (individual_id);
