CREATE TABLE gedcom_xref_counter (
    tree_id     UUID NOT NULL REFERENCES gedcom_tree (id) ON DELETE CASCADE,
    prefix      TEXT NOT NULL CHECK (prefix IN ('I', 'F', 'N', 'S')),
    next_value  BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (tree_id, prefix)
);

CREATE OR REPLACE FUNCTION next_gedcom_xref(p_tree_id UUID, p_prefix TEXT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_num BIGINT;
BEGIN
    IF p_prefix NOT IN ('I', 'F', 'N', 'S') THEN
        RAISE EXCEPTION 'Invalid GEDCOM xref prefix: %', p_prefix;
    END IF;

    INSERT INTO gedcom_xref_counter (tree_id, prefix, next_value)
    VALUES (p_tree_id, p_prefix, 2)
    ON CONFLICT (tree_id, prefix)
    DO UPDATE SET next_value = gedcom_xref_counter.next_value + 1
    RETURNING next_value - 1 INTO v_num;

    RETURN p_prefix || v_num;
END;
$$;

INSERT INTO gedcom_xref_counter (tree_id, prefix, next_value)
SELECT id, prefix, 1
FROM gedcom_tree
CROSS JOIN (VALUES ('I'), ('F'), ('N'), ('S')) AS prefixes (prefix);
