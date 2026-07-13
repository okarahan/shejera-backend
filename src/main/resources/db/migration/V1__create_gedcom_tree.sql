CREATE TABLE gedcom_tree (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    gedcom_version TEXT NOT NULL DEFAULT '7.0',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO gedcom_tree (name) VALUES ('Shejera');
