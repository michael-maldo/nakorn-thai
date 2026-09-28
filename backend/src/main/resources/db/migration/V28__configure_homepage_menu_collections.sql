CREATE TABLE menu_homepage_settings (
    id smallint PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE menu_homepage_collection (
    settings_id smallint NOT NULL REFERENCES menu_homepage_settings(id),
    display_order integer NOT NULL CHECK (display_order >= 0),
    collection_id uuid NOT NULL REFERENCES menu_collection(id),
    PRIMARY KEY (settings_id, display_order),
    UNIQUE (settings_id, collection_id) DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO menu_homepage_settings (id) VALUES (1);
-- Start with the first published collection; staff can replace or clear this selection.
INSERT INTO menu_homepage_collection (settings_id, display_order, collection_id)
SELECT 1, 0, id FROM menu_collection WHERE status = 'PUBLISHED'
ORDER BY display_order, id LIMIT 1;
