ALTER TABLE menu_item_image
    ADD COLUMN rotation integer NOT NULL DEFAULT 0
    CONSTRAINT menu_item_image_rotation_range CHECK (rotation BETWEEN -180 AND 180);
