-- Reusable choices carry no global price. An explicit item price makes a choice selectable.
ALTER TABLE menu_option ADD CONSTRAINT menu_option_id_group_unique UNIQUE (id, option_group_id);
CREATE TABLE menu_item_option_price (
    menu_item_id UUID NOT NULL,
    option_group_id UUID NOT NULL,
    option_id UUID NOT NULL,
    price_delta_minor BIGINT NOT NULL CHECK (price_delta_minor >= 0),
    PRIMARY KEY (menu_item_id, option_group_id, option_id),
    FOREIGN KEY (menu_item_id, option_group_id)
        REFERENCES menu_item_option_group(menu_item_id, option_group_id) ON DELETE CASCADE,
    FOREIGN KEY (option_id, option_group_id)
        REFERENCES menu_option(id, option_group_id) ON DELETE RESTRICT
);
CREATE INDEX menu_item_option_price_option ON menu_item_option_price(option_id, option_group_id);
INSERT INTO menu_item_option_price (menu_item_id, option_group_id, option_id, price_delta_minor)
SELECT a.menu_item_id, a.option_group_id, o.id, o.price_delta_minor
FROM menu_item_option_group a JOIN menu_option o ON o.option_group_id = a.option_group_id;
ALTER TABLE menu_option DROP COLUMN price_delta_minor;
