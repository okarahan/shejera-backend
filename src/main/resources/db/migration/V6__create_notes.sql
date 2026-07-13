CREATE TABLE note (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tree_id     UUID NOT NULL REFERENCES gedcom_tree (id) ON DELETE CASCADE,
    xref        TEXT,
    text        TEXT NOT NULL,
    is_shared   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tree_id, xref)
);

CREATE INDEX idx_note_tree_id ON note (tree_id);

CREATE TABLE note_link (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id     UUID NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL CHECK (entity_type IN ('individual', 'family', 'individual_event', 'family_event')),
    entity_id   UUID NOT NULL,
    note_type   TEXT NOT NULL DEFAULT 'general' CHECK (note_type IN ('biography', 'research', 'general'))
);

CREATE INDEX idx_note_link_note_id ON note_link (note_id);
CREATE INDEX idx_note_link_entity ON note_link (entity_type, entity_id);
