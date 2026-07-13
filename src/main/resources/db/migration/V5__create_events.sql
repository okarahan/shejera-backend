CREATE TABLE individual_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    individual_id   UUID NOT NULL REFERENCES individual (id) ON DELETE CASCADE,
    tag             TEXT NOT NULL,
    event_type      TEXT,
    date_text       TEXT,
    date_sort       DATE,
    place_id        UUID REFERENCES place (id) ON DELETE SET NULL,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_individual_event_individual_id ON individual_event (individual_id);
CREATE INDEX idx_individual_event_place_id ON individual_event (place_id);
CREATE INDEX idx_individual_event_tag ON individual_event (tag);

CREATE TABLE family_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id   UUID NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    tag         TEXT NOT NULL,
    event_type  TEXT,
    date_text   TEXT,
    date_sort   DATE,
    place_id    UUID REFERENCES place (id) ON DELETE SET NULL,
    description TEXT,
    sort_order  INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_family_event_family_id ON family_event (family_id);
CREATE INDEX idx_family_event_place_id ON family_event (place_id);
CREATE INDEX idx_family_event_tag ON family_event (tag);
