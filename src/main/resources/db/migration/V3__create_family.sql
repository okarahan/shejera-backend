CREATE TABLE family (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tree_id     UUID NOT NULL REFERENCES gedcom_tree (id) ON DELETE CASCADE,
    xref        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tree_id, xref)
);

CREATE INDEX idx_family_tree_id ON family (tree_id);

CREATE TABLE family_spouse (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       UUID NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    individual_id   UUID NOT NULL REFERENCES individual (id) ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('HUSB', 'WIFE')),
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (family_id, individual_id)
);

CREATE INDEX idx_family_spouse_family_id ON family_spouse (family_id);
CREATE INDEX idx_family_spouse_individual_id ON family_spouse (individual_id);

CREATE TABLE family_child (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       UUID NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    individual_id   UUID NOT NULL REFERENCES individual (id) ON DELETE CASCADE,
    pedigree        TEXT CHECK (pedigree IN ('BIRTH', 'ADOPTED', 'FOSTER', 'SEALING', 'STEP', 'CHALLENGED', 'DISPROVEN')),
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (family_id, individual_id)
);

CREATE INDEX idx_family_child_family_id ON family_child (family_id);
CREATE INDEX idx_family_child_individual_id ON family_child (individual_id);
