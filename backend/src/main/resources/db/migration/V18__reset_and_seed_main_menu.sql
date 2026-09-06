-- V16__reset_and_seed_main_menu.sql
--
-- Clean reset of menu and ordering data, followed by a seed of the supplied
-- Nakorn Thai printed menu.
--
-- PRESERVED:
--   flyway_schema_history
--   staff_user
--   staff_session
--   reservation
--
-- CLEARED:
--   restaurant_order_event
--   restaurant_order_item_option
--   restaurant_order_item
--   restaurant_order
--   menu option/configuration tables
--   menu variation metadata
--   menu items/categories/collections
--   allergens/dietary tags
--
-- IMPORTANT:
--   Allergen data is intentionally NOT inferred from ingredient names.
--   Only the printed "(GF)" dietary marking is seeded as Gluten Free.
--   Menu items remain allergen_review_status = NOT_REVIEWED.
--
-- Prices are stored in integer AUD minor units (cents).
-- Example: $23.90 = 2390.


-- Explicitly preserved by this migration:
--   flyway_schema_history
--   staff_user
--   staff_session
--   reservation
--   function_enquiry
--
-- The following V16 order-support tables are intentionally reset because
-- they belong to restaurant-order state and reference restaurant_order:
--   order_payment
--   order_verification
--   order_tracking_grant
--
TRUNCATE TABLE
    public.order_payment,
    public.order_verification,
    public.order_tracking_grant,
    public.restaurant_order_event,
    public.restaurant_order_item_option,
    public.restaurant_order_item,
    public.restaurant_order,
    public.menu_item_variation_allergen,
    public.menu_item_variation_dietary_tag,
    public.menu_item_allergen,
    public.menu_item_dietary_tag,
    public.menu_item_image,
    public.menu_item_option_group,
    public.menu_option,
    public.menu_option_group,
    public.menu_collection_item,
    public.menu_collection_schedule,
    public.menu_collection_category,
    public.menu_item_variation,
    public.menu_item,
    public.menu_category,
    public.menu_collection,
    public.allergen,
    public.dietary_tag;


-- -------------------------------------------------------------------------
-- Menu collection
-- -------------------------------------------------------------------------
INSERT INTO public.menu_collection
    (id, name, slug, description, status, display_order, is_active, timezone)
VALUES
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'Main Menu', 'main-menu',
     'Nakorn Thai main restaurant menu.', 'PUBLISHED', 1, true, 'Australia/Melbourne');

-- Main Restaurant Menu availability
-- -------------------------------
-- status = PUBLISHED
-- is_active = true
-- starts_at / ends_at remain NULL
-- no menu_collection_schedule rows are inserted
--
-- Therefore the Main Restaurant Menu is always available.
--
-- Future examples:
--
-- Summer Promo:
--   menu_collection.starts_at / ends_at define the seasonal date range.
--
-- Lunch Menu:
--   add WEEKLY schedule rows for Mon-Fri 11:30-15:00.
--
-- Valentine's Day:
--   add SPECIFIC_DATE schedule row for 14 February with an optional
--   start_time / end_time window.


-- -------------------------------------------------------------------------
-- Categories
-- -------------------------------------------------------------------------
INSERT INTO public.menu_category
    (id, name, slug, description, display_order, is_active)
VALUES
    ('f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Entree', 'entree', 'Main menu category.', 1, true),
    ('85edfafc-8538-521a-9639-4287251b68fe', 'Fish', 'fish', 'Main menu category.', 2, true),
    ('c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Thai Curry', 'thai-curry', 'Main menu category.', 3, true),
    ('7a8e3174-ab88-562d-86ed-10ce45463c64', 'Stir-Fry', 'stir-fry', 'Main menu category.', 4, true),
    ('385cd238-d7a8-5ab7-b35b-b41e593e2b24', 'BBQ and Grilled', 'bbq-grilled', 'Main menu category.', 5, true),
    ('f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 'Noodles Stir-Fry', 'noodles-stir-fry', 'Main menu category.', 6, true),
    ('89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 'Fried Rice', 'fried-rice', 'Main menu category.', 7, true);

-- -------------------------------------------------------------------------
-- Collection categories for Main Menu
-- -------------------------------------------------------------------------
INSERT INTO public.menu_collection_category
    (id, collection_id, category_id, display_order, version)
VALUES
    ('26f34e27-a0a8-54db-8726-4c3cce7ae348', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 1, 0),
    ('36265e2d-acc3-575c-afe9-216177960351', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '85edfafc-8538-521a-9639-4287251b68fe', 2, 0),
    ('75f3d4cc-8734-57a0-866c-096908eb65cc', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 3, 0),
    ('cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '7a8e3174-ab88-562d-86ed-10ce45463c64', 4, 0),
    ('5fd14c86-4ce8-536a-8f62-5d084ff61405', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '385cd238-d7a8-5ab7-b35b-b41e593e2b24', 5, 0),
    ('584a65b6-16f0-5899-ae0f-4e352b7b4ad4', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 6, 0),
    ('5e7ddb4d-9919-5c33-b28d-333771aa58dc', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 7, 0);

-- -------------------------------------------------------------------------
-- Dietary tags
-- -------------------------------------------------------------------------
INSERT INTO public.dietary_tag
    (id, code, name, description, display_order, is_active)
VALUES
    ('b814e25a-44b2-5bb7-aa64-79b1f763bdee', 'GF', 'Gluten Free',
     'Gluten-free as indicated on the supplied printed menu.', 1, true);

-- -------------------------------------------------------------------------
-- Menu items
-- -------------------------------------------------------------------------
INSERT INTO public.menu_item
    (id, category_id, name, slug, description, status, is_available,
     display_order, allergen_review_status, version)
VALUES
    ('34126363-fbb5-569a-afcd-46daaaf95119', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Prawn Crackers with Chilli Jam', 'prawn-crackers-with-chilli-jam', 'Served with our sweet chilli jam.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('ff11db97-2992-5b75-9784-81956be02321', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Roti (2 Pieces)', 'roti-2-pieces', 'Crispy, buttery and flaky flatbread. Served with homemade peanut sauce.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('cc3991d8-4e34-5dc6-9a7c-f2f03a61e712', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Chicken Spring Rolls (4 Pieces)', 'chicken-spring-rolls-4-pieces', 'Served with sweet chilli sauce.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('7dec9482-b162-544e-8c6b-cebb8b6417f3', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Vegetable Spring Rolls (4 Pieces)', 'vegetable-spring-rolls-4-pieces', 'Served with sweet chilli sauce.', 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('fe42722b-468b-55b9-b4bf-b29175672448', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Vegetable Curry Puffs (4 Pieces)', 'vegetable-curry-puffs-4-pieces', 'Puff pastry filled with potatoes, carrots and peas. Served with sweet chilli sauce.', 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('8e1555ab-ef0f-562b-a159-b7a7fd7774db', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Fish Cakes (4 Pieces)', 'fish-cakes-4-pieces', 'Fish paste mixed with Thai spices served with sweet chilli sauce.', 'PUBLISHED', true, 6, 'NOT_REVIEWED', 0),
    ('8964f5e5-f863-5a7b-9004-5315d2f7e486', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Salt & Pepper Calamari', 'salt-and-pepper-calamari', 'Lightly battered calamari and side of tartar sauce.', 'PUBLISHED', true, 7, 'NOT_REVIEWED', 0),
    ('e051f03f-3341-54e6-9d77-9037d2520b5b', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Calamari Rings', 'calamari-rings', 'Battered calamari rings served with sweet chilli sauce.', 'PUBLISHED', true, 8, 'NOT_REVIEWED', 0),
    ('05e94fae-4924-59e3-a432-abf694ba5838', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Tempura Prawns', 'tempura-prawns', 'Served with sweet chilli sauce.', 'PUBLISHED', true, 9, 'NOT_REVIEWED', 0),
    ('01bf8852-06cc-5418-ac53-8a1301d84faf', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Satay Chicken (4 Skewers)', 'satay-chicken-4-skewers', 'Chicken tenderloin marinated in Thai spices. Skewer served with homemade peanut sauce.', 'PUBLISHED', true, 10, 'NOT_REVIEWED', 0),
    ('1608f145-0f18-55d2-af0f-82e913c3f29f', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Vegetable San Choy Bow', 'vegetable-san-choy-bow', 'Served with sweet chilli sauce.', 'PUBLISHED', true, 11, 'NOT_REVIEWED', 0),
    ('659b5509-f838-5891-9791-07c40ca8140c', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Chicken Pandan', 'chicken-pandan', 'Tapioca, rice noodles, fish sauce, soy sauce.', 'PUBLISHED', true, 12, 'NOT_REVIEWED', 0),
    ('f90fa713-8d21-5b37-8b38-94690c81f018', 'f52a0ed8-a260-5489-bcc1-63dabf71ba9d', 'Mix Platte', 'mix-platte', 'Mix: 1 x Chicken Spring Roll, 1 x Satay Chicken, 1 x Curry Puff, 1 x Fish Cake.', 'PUBLISHED', true, 13, 'NOT_REVIEWED', 0),
    ('793ed136-35c9-530d-834e-fa2eff2ffeaa', '85edfafc-8538-521a-9639-4287251b68fe', 'Whole Barramundi Fish', 'whole-barramundi-fish', 'Deep-Fried Only', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('3594448a-54a5-59c9-9f51-e558d3d90eed', '85edfafc-8538-521a-9639-4287251b68fe', 'Fish Fillets', 'fish-fillets', 'Deep-Fried Only', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('2576add4-cce5-5635-965d-bed257c12ac7', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Green Curry', 'green-curry', 'With pumpkin, green beans, zucchini, capsicum, bamboo shoots and basil.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('49edf648-f45e-5d3b-be63-47e1c1193d55', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Red Curry', 'red-curry', 'With pumpkin, green beans, zucchini, capsicum, bamboo and basil.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('aa6fe0ee-fecc-5c8b-acac-056a511f12f3', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Yellow Curry', 'yellow-curry', 'With potatoes, carrot and onion.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('3e07bb88-81b6-55fb-8719-8b81f5c751c3', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Panang Curry', 'panang-curry', 'With capsicum and green beans, red capsicum and kaffir lime leaves.', 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('bc890114-3190-564f-8e9a-583db0a1e599', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Massaman Beef', 'massaman-beef', 'Slow cooked beef with potato and carrot topped with peanuts and fried onion.', 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('4da014ef-dd26-59d5-a095-f21eb2e0cef0', 'c7fa5f88-f83c-5d23-9f70-e742f2a38b52', 'Jungle Curry', 'jungle-curry', 'Red curry paste, young green pepper, mixed vegetable, mushroom and paired herbs.', 'PUBLISHED', true, 6, 'NOT_REVIEWED', 0),
    ('8e57d123-23b4-5e89-bd3f-d6d54fb2a8c9', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Cashew Nuts with Chilli Jam', 'cashew-nuts-with-chilli-jam', 'Onion, capsicum, zucchini, broccoli, cashew nut and spring onion.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('4b094692-84e8-5391-a1cd-b8bd5a9ad56e', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Sweet and Sour (Pad Priew Wang)', 'sweet-and-sour-pad-priew-wang', 'Tomato, cucumber, onion, capsicum, pineapple and spring onion.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('3e1467b3-5b3e-58de-b556-ad6b2383efc9', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Fresh Ginger (Pad Khing)', 'fresh-ginger-pad-khing', 'Fresh ginger, onion, fresh chilli, black mushroom, mushroom and spring onion.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('ad54fef3-93d2-5063-aa11-ae6f71710ec5', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Pad Krapao', 'pad-krapao', 'Garlic, chilli, onion, capsicum, zucchini, carrot, broccoli, green beans, bamboo shoot and basil.', 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('7d4da000-020e-5d80-aec2-674e091e213f', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Garlic and Pepper', 'garlic-and-pepper', 'Broccoli, carrot, zucchini, snow peas, garlic, onion and black pepper.', 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('e7b3fc56-6721-5ca7-89f8-546c080ac685', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Satay Stir-Fried', 'satay-stir-fried', 'Broccoli, carrot, zucchini, mushroom and bok choy with homemade peanut sauce.', 'PUBLISHED', true, 6, 'NOT_REVIEWED', 0),
    ('fb37d31e-c543-51f8-aca1-7deaa4990d89', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Stir-Fried Basil with Eggplant', 'stir-fried-basil-with-eggplant', 'Garlic, chilli, onion, eggplant, capsicum, zucchini, broccoli and basil.', 'PUBLISHED', true, 7, 'NOT_REVIEWED', 0),
    ('ac162e2d-fbff-5ed9-b58a-45299595ae94', '7a8e3174-ab88-562d-86ed-10ce45463c64', 'Zucchini Tofu Platter', 'zucchini-tofu-platter', 'Onion, capsicum, zucchini, broccoli, cashew nut and spring onion.', 'PUBLISHED', true, 8, 'NOT_REVIEWED', 0),
    ('8d411e9f-2847-5008-9228-f4757d83cbb0', '385cd238-d7a8-5ab7-b35b-b41e593e2b24', 'Grilled Marinated Chicken', 'grilled-marinated-chicken', 'Marinated chicken fillet in Thai spices and BBQ in real Thai tradition.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('a1ff7e60-297d-5416-885d-c86228c6c329', '385cd238-d7a8-5ab7-b35b-b41e593e2b24', 'Grilled Marinated Pork', 'grilled-marinated-pork', 'Pork neck marinated in Thai spices and BBQ in real Thai tradition served with dried chilli and tamarind relish.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('ad4f1fe2-f094-5861-a759-b48b29882e1d', '385cd238-d7a8-5ab7-b35b-b41e593e2b24', 'Grilled Marinated Beef - 350G', 'grilled-marinated-beef-350g', 'Grass fed eye fillet marinated and grilled with special sauce served with side of salad and chilli dipping sauce.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('88698b0e-72d3-54a3-8fb7-7645d6e09e13', 'f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 'Pad Thai', 'pad-thai', 'Stir-fried rice noodles with egg, tofu, bean sprouts and crushed.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('fc73b860-2475-5d4c-bbd9-1e27f8872f2c', 'f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 'Pad See Ew', 'pad-see-ew', 'Stir-fried flat noodles with egg and Chinese broccoli.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('ffd46610-0704-5e7c-91cf-fa02db4ad1af', 'f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 'Pad Kee Mao', 'pad-kee-mao', 'Stir-fried thick rice noodles with vegetable, fresh chilli and Thai basil.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('f454ac07-8670-56f2-92b1-beb4da66fe14', 'f9f7fee3-bb72-59c9-abf9-ad337a5f9307', 'Egg Noodles with BBQ Chicken', 'egg-noodles-with-bbq-chicken', 'Egg noodles with Chinese broccoli and bean sprouts, topped with our famous BBQ chicken.', 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('bbb36e05-9ec7-551a-a0a4-1417c1a59693', '89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 'Fried Rice', 'fried-rice', 'With egg, onion, bok choy, carrot and broccoli.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('d6413c22-8d4b-5faf-b088-1874bcee0199', '89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 'Chilli Fried Rice', 'chilli-fried-rice', 'With egg, onion, bok choy, carrot, broccoli and fresh chilli.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('151c26bf-c14a-5842-aca7-8d3ee15124ab', '89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 'Tom Yum Fried Rice', 'tom-yum-fried-rice', 'Hot and spicy Tom Yum fried rice with Tom Yum flavour, lemon grass, lime leaf, onion, bok choy, carrot, broccoli and garlic.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('3977c2d9-d02b-5895-8457-31821795f5d5', '89f4b6b1-b396-5c4a-8daf-7ca537518bc5', 'Pineapple Fried Rice', 'pineapple-fried-rice', 'Fried rice with pineapple, cashew nut, onion, spring onion and mixed vegetable.', 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0);

-- -------------------------------------------------------------------------
-- One default/base variation per item. This stores the base menu price.
-- -------------------------------------------------------------------------
INSERT INTO public.menu_item_variation
    (id, menu_item_id, name, sku, price_minor, currency, is_default,
     is_active, is_available, display_order, allergen_review_status, version)
VALUES
    ('f25ea3b6-4b19-5d10-ae69-3dc18028c3ab', '34126363-fbb5-569a-afcd-46daaaf95119', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('d8adc444-eeba-5816-bb78-aefa382effb3', 'ff11db97-2992-5b75-9784-81956be02321', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('241776d9-3ae4-56fb-b84f-ed3f18c872e4', 'cc3991d8-4e34-5dc6-9a7c-f2f03a61e712', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('66ad4cbe-1471-5cae-885c-968adf801274', '7dec9482-b162-544e-8c6b-cebb8b6417f3', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('361e8b96-5bbf-5b2c-8ade-3cdb2403ff53', 'fe42722b-468b-55b9-b4bf-b29175672448', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('b92d3b13-e93a-5848-a13a-060600110827', '8e1555ab-ef0f-562b-a159-b7a7fd7774db', 'Standard', NULL, 1190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('09880360-7870-560f-9111-9b85f5c65e4f', '8964f5e5-f863-5a7b-9004-5315d2f7e486', 'Standard', NULL, 1790, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('08dc1305-cafc-5fde-9495-9dbe00f71bd5', 'e051f03f-3341-54e6-9d77-9037d2520b5b', 'Standard', NULL, 1790, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('7f970979-03a6-5ca7-a2ee-f32b012fa0e1', '05e94fae-4924-59e3-a432-abf694ba5838', 'Standard', NULL, 1790, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f7c8231f-6f5f-511e-ae65-86bbd27b8b82', '01bf8852-06cc-5418-ac53-8a1301d84faf', 'Standard', NULL, 1390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('c2845739-a373-5a1d-a2e0-e28d0940e452', '1608f145-0f18-55d2-af0f-82e913c3f29f', 'Standard', NULL, 1790, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('764d5535-74a8-5fa3-bbf8-199cb221f542', '659b5509-f838-5891-9791-07c40ca8140c', 'Standard', NULL, 1590, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('d5ec1d4d-09cc-5bac-b403-b13326dbe0e6', 'f90fa713-8d21-5b37-8b38-94690c81f018', 'Standard', NULL, 1690, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('aef3d605-8ae8-5aa9-ae4e-cce7ab0cc7ce', '793ed136-35c9-530d-834e-fa2eff2ffeaa', 'Standard', NULL, 4590, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('6e1fcc4e-6e42-52d1-a49b-0e4d795d1489', '3594448a-54a5-59c9-9f51-e558d3d90eed', 'Standard', NULL, 2890, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('fbc16f62-bb60-5743-bb58-93cce3e14edb', '2576add4-cce5-5635-965d-bed257c12ac7', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('2001db84-9b94-552d-b501-b6d8bd71dc25', '49edf648-f45e-5d3b-be63-47e1c1193d55', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('ba3c2330-d1de-594b-b017-cc90140c7bd6', 'aa6fe0ee-fecc-5c8b-acac-056a511f12f3', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('8f45983e-63c7-5100-a437-2cbe42b50158', '3e07bb88-81b6-55fb-8719-8b81f5c751c3', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('d0a8ea0c-4437-5573-816a-44a001c52c11', 'bc890114-3190-564f-8e9a-583db0a1e599', 'Standard', NULL, 2590, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('7e9841af-04df-58c2-b763-309fddc43502', '4da014ef-dd26-59d5-a095-f21eb2e0cef0', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('e5ff5125-78ee-5b62-87cd-03e722a40de4', '8e57d123-23b4-5e89-bd3f-d6d54fb2a8c9', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('8fcbc27c-6167-58fb-b2b6-ddd7e9d0ce45', '4b094692-84e8-5391-a1cd-b8bd5a9ad56e', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('572b8309-b8d9-5d3e-a559-bd646197219c', '3e1467b3-5b3e-58de-b556-ad6b2383efc9', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('3796d20a-df6b-528d-b862-3fcc798324a6', 'ad54fef3-93d2-5063-aa11-ae6f71710ec5', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('04f87333-4133-55ea-9ab8-c835614f390e', '7d4da000-020e-5d80-aec2-674e091e213f', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('381a39de-a06b-5357-b711-42e03f8b1f59', 'e7b3fc56-6721-5ca7-89f8-546c080ac685', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('fac83f43-6261-5b26-a2b8-fef4fd342f78', 'fb37d31e-c543-51f8-aca1-7deaa4990d89', 'Standard', NULL, 2390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('eb47d7df-a5c2-50a8-acfe-bb2e942de065', 'ac162e2d-fbff-5ed9-b58a-45299595ae94', 'Standard', NULL, 2690, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('e530a52d-bf41-599e-8f76-f780c58a02a2', '8d411e9f-2847-5008-9228-f4757d83cbb0', 'Standard', NULL, 2490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('c200e04a-f97b-53fe-9a37-dcc4b4d75e14', 'a1ff7e60-297d-5416-885d-c86228c6c329', 'Standard', NULL, 2590, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('29223229-c239-57a3-bfa6-42f407eb1bed', 'ad4f1fe2-f094-5861-a759-b48b29882e1d', 'Standard', NULL, 2890, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('48ce04d3-cf27-56e6-b5d1-4bd23b7416dd', '88698b0e-72d3-54a3-8fb7-7645d6e09e13', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('e0a27411-98be-56c6-aecf-c2518ade60f5', 'fc73b860-2475-5d4c-bbd9-1e27f8872f2c', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('fafaef3c-ae24-5e4d-bba7-c65457217d7c', 'ffd46610-0704-5e7c-91cf-fa02db4ad1af', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('5a4a62be-2008-5310-879f-d30253089419', 'f454ac07-8670-56f2-92b1-beb4da66fe14', 'Standard', NULL, 2190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('754324f7-beeb-585d-9f14-cc3b92cbd89b', 'bbb36e05-9ec7-551a-a0a4-1417c1a59693', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('5c0d0aae-9032-562a-8c89-2261144b180f', 'd6413c22-8d4b-5faf-b088-1874bcee0199', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('acac9da0-8010-5696-a749-5064c89ee503', '151c26bf-c14a-5842-aca7-8d3ee15124ab', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f8bdca08-d616-5b30-a7df-f7d8bbaf54bb', '3977c2d9-d02b-5895-8457-31821795f5d5', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0);

-- -------------------------------------------------------------------------
-- Add all menu items to Main Menu in printed-menu order.
-- collection_category_id controls the category within THIS collection.
-- price_override_minor is NULL, so the item's normal/default variation price
-- is used. A future Lunch/Valentine's/Father's Day collection can override it.
-- -------------------------------------------------------------------------
INSERT INTO public.menu_collection_item
    (collection_id, menu_item_id, collection_category_id, display_order,
     price_override_minor, version)
VALUES
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '34126363-fbb5-569a-afcd-46daaaf95119', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 1, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ff11db97-2992-5b75-9784-81956be02321', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 2, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'cc3991d8-4e34-5dc6-9a7c-f2f03a61e712', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 3, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '7dec9482-b162-544e-8c6b-cebb8b6417f3', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 4, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'fe42722b-468b-55b9-b4bf-b29175672448', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 5, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '8e1555ab-ef0f-562b-a159-b7a7fd7774db', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 6, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '8964f5e5-f863-5a7b-9004-5315d2f7e486', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 7, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'e051f03f-3341-54e6-9d77-9037d2520b5b', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 8, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '05e94fae-4924-59e3-a432-abf694ba5838', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 9, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '01bf8852-06cc-5418-ac53-8a1301d84faf', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 10, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '1608f145-0f18-55d2-af0f-82e913c3f29f', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 11, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '659b5509-f838-5891-9791-07c40ca8140c', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 12, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'f90fa713-8d21-5b37-8b38-94690c81f018', '26f34e27-a0a8-54db-8726-4c3cce7ae348', 13, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '793ed136-35c9-530d-834e-fa2eff2ffeaa', '36265e2d-acc3-575c-afe9-216177960351', 14, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3594448a-54a5-59c9-9f51-e558d3d90eed', '36265e2d-acc3-575c-afe9-216177960351', 15, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '2576add4-cce5-5635-965d-bed257c12ac7', '75f3d4cc-8734-57a0-866c-096908eb65cc', 16, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '49edf648-f45e-5d3b-be63-47e1c1193d55', '75f3d4cc-8734-57a0-866c-096908eb65cc', 17, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'aa6fe0ee-fecc-5c8b-acac-056a511f12f3', '75f3d4cc-8734-57a0-866c-096908eb65cc', 18, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3e07bb88-81b6-55fb-8719-8b81f5c751c3', '75f3d4cc-8734-57a0-866c-096908eb65cc', 19, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'bc890114-3190-564f-8e9a-583db0a1e599', '75f3d4cc-8734-57a0-866c-096908eb65cc', 20, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '4da014ef-dd26-59d5-a095-f21eb2e0cef0', '75f3d4cc-8734-57a0-866c-096908eb65cc', 21, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '8e57d123-23b4-5e89-bd3f-d6d54fb2a8c9', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 22, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '4b094692-84e8-5391-a1cd-b8bd5a9ad56e', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 23, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3e1467b3-5b3e-58de-b556-ad6b2383efc9', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 24, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ad54fef3-93d2-5063-aa11-ae6f71710ec5', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 25, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '7d4da000-020e-5d80-aec2-674e091e213f', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 26, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'e7b3fc56-6721-5ca7-89f8-546c080ac685', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 27, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'fb37d31e-c543-51f8-aca1-7deaa4990d89', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 28, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ac162e2d-fbff-5ed9-b58a-45299595ae94', 'cbe95654-c74b-5636-8e5b-fedb65ab4bf4', 29, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '8d411e9f-2847-5008-9228-f4757d83cbb0', '5fd14c86-4ce8-536a-8f62-5d084ff61405', 30, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'a1ff7e60-297d-5416-885d-c86228c6c329', '5fd14c86-4ce8-536a-8f62-5d084ff61405', 31, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ad4f1fe2-f094-5861-a759-b48b29882e1d', '5fd14c86-4ce8-536a-8f62-5d084ff61405', 32, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '88698b0e-72d3-54a3-8fb7-7645d6e09e13', '584a65b6-16f0-5899-ae0f-4e352b7b4ad4', 33, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'fc73b860-2475-5d4c-bbd9-1e27f8872f2c', '584a65b6-16f0-5899-ae0f-4e352b7b4ad4', 34, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ffd46610-0704-5e7c-91cf-fa02db4ad1af', '584a65b6-16f0-5899-ae0f-4e352b7b4ad4', 35, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'f454ac07-8670-56f2-92b1-beb4da66fe14', '584a65b6-16f0-5899-ae0f-4e352b7b4ad4', 36, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'bbb36e05-9ec7-551a-a0a4-1417c1a59693', '5e7ddb4d-9919-5c33-b28d-333771aa58dc', 37, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd6413c22-8d4b-5faf-b088-1874bcee0199', '5e7ddb4d-9919-5c33-b28d-333771aa58dc', 38, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '151c26bf-c14a-5842-aca7-8d3ee15124ab', '5e7ddb4d-9919-5c33-b28d-333771aa58dc', 39, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3977c2d9-d02b-5895-8457-31821795f5d5', '5e7ddb4d-9919-5c33-b28d-333771aa58dc', 40, NULL, 0);

-- -------------------------------------------------------------------------
-- Printed GF markings
-- -------------------------------------------------------------------------
INSERT INTO public.menu_item_dietary_tag
    (menu_item_id, dietary_tag_id, notes, verified_at, version)
VALUES
    ('01bf8852-06cc-5418-ac53-8a1301d84faf', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('659b5509-f838-5891-9791-07c40ca8140c', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('2576add4-cce5-5635-965d-bed257c12ac7', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('49edf648-f45e-5d3b-be63-47e1c1193d55', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('aa6fe0ee-fecc-5c8b-acac-056a511f12f3', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('3e07bb88-81b6-55fb-8719-8b81f5c751c3', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('bc890114-3190-564f-8e9a-583db0a1e599', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('4da014ef-dd26-59d5-a095-f21eb2e0cef0', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0);

-- -------------------------------------------------------------------------
-- Option groups
-- -------------------------------------------------------------------------
INSERT INTO public.menu_option_group
    (id, code, name, selection_type, is_active, version)
VALUES
    ('85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 'fish-sauce', 'Fish Sauce', 'SINGLE', true, 0),
    ('154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'thai-curry-protein', 'Protein', 'SINGLE', true, 0),
    ('32e0d6d6-f621-5a49-9f8f-12bc97998042', 'noodle-protein', 'Protein', 'SINGLE', true, 0);

-- -------------------------------------------------------------------------
-- Options and additive price deltas
-- -------------------------------------------------------------------------
INSERT INTO public.menu_option
    (id, option_group_id, code, name, price_delta_minor, currency,
     display_order, is_active, version)
VALUES
    ('0d53dfac-c56a-5832-9056-7e05432e32b8', '85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 'chilli-tamarind', 'Chilli & Tamarind Sauce', 0, 'AUD', 1, true, 0),
    ('39193316-2f12-5776-930e-a633177c5f71', '85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 'fresh-ginger', 'Fresh Ginger Sauce', 0, 'AUD', 2, true, 0),
    ('004a3383-10d9-53d7-82ce-55ea24265c7d', '85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 'sweet-sour', 'Sweet & Sour Sauce', 0, 'AUD', 3, true, 0),
    ('664d4fdd-ec5c-5389-8ef5-25eb99ac5dbc', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'beef', 'Beef', 0, 'AUD', 1, true, 0),
    ('2b08b9a1-2cc6-5780-81b8-4ad5818ffca2', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'chicken', 'Chicken', 0, 'AUD', 2, true, 0),
    ('afd4ecd6-f5dc-510d-a90d-1950b9e6e7b7', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'veg-tofu', 'Veg & Tofu', 0, 'AUD', 3, true, 0),
    ('111227db-7b26-5316-9488-343bba9a892e', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'prawns', 'Prawns', 600, 'AUD', 4, true, 0),
    ('418b66f7-f657-5114-aa90-a3c6aab9d5eb', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'seafood', 'Seafood', 800, 'AUD', 5, true, 0),
    ('2fa27c7a-8dc3-5496-8ff4-e4f066d9a347', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'crispy-pork', 'Crispy Pork', 500, 'AUD', 6, true, 0),
    ('1bf57486-7b27-5d86-b4d5-ac60af361f61', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 'fish', 'Fish', 600, 'AUD', 7, true, 0),
    ('0e749bd6-b70c-5363-9282-3802dcf9b608', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'beef', 'Beef', 0, 'AUD', 1, true, 0),
    ('8cba193e-8a30-5d63-acd6-0c0b2ed919e2', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'chicken', 'Chicken', 0, 'AUD', 2, true, 0),
    ('007cb34f-5daa-5a77-8ee5-00c573806be7', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'veg-tofu', 'Veg & Tofu', 0, 'AUD', 3, true, 0),
    ('e437d1be-b8ae-58b4-a6f2-f97195148f14', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'prawns', 'Prawns', 600, 'AUD', 4, true, 0),
    ('baf2190e-65be-536f-ae0f-6b03a10a2522', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'seafood', 'Seafood', 800, 'AUD', 5, true, 0),
    ('f8fe0b64-efa9-569e-8df1-4f2d79476ff4', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'crispy-pork', 'Crispy Pork', 500, 'AUD', 6, true, 0),
    ('d7998b23-726c-5501-b3c0-19d8ee8642af', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 'fish', 'Fish', 600, 'AUD', 7, true, 0);

-- -------------------------------------------------------------------------
-- Attach option groups only to the menu items that use them.
-- min_selections=1/max_selections=1 makes each of these a required
-- single-choice selector.
-- -------------------------------------------------------------------------
INSERT INTO public.menu_item_option_group
    (menu_item_id, option_group_id, min_selections, max_selections,
     display_order, version)
VALUES
    ('793ed136-35c9-530d-834e-fa2eff2ffeaa', '85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 1, 1, 1, 0),
    ('3594448a-54a5-59c9-9f51-e558d3d90eed', '85fb236b-8ff5-51a2-bfa5-b3a0c658be4a', 1, 1, 1, 0),
    ('2576add4-cce5-5635-965d-bed257c12ac7', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('49edf648-f45e-5d3b-be63-47e1c1193d55', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('aa6fe0ee-fecc-5c8b-acac-056a511f12f3', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('3e07bb88-81b6-55fb-8719-8b81f5c751c3', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('bc890114-3190-564f-8e9a-583db0a1e599', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('4da014ef-dd26-59d5-a095-f21eb2e0cef0', '154a782b-9211-5bb0-a01b-6bfe0e8717b8', 1, 1, 1, 0),
    ('88698b0e-72d3-54a3-8fb7-7645d6e09e13', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 1, 1, 1, 0),
    ('fc73b860-2475-5d4c-bbd9-1e27f8872f2c', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 1, 1, 1, 0),
    ('ffd46610-0704-5e7c-91cf-fa02db4ad1af', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 1, 1, 1, 0),
    ('f454ac07-8670-56f2-92b1-beb4da66fe14', '32e0d6d6-f621-5a49-9f8f-12bc97998042', 1, 1, 1, 0);

-- -------------------------------------------------------------------------
-- Verification guards
-- -------------------------------------------------------------------------
DO $$
DECLARE
    menu_item_count integer;
    default_variation_count integer;
    collection_item_count integer;
    collection_category_count integer;
BEGIN
    SELECT count(*) INTO menu_item_count
    FROM public.menu_item;

    SELECT count(*) INTO default_variation_count
    FROM public.menu_item_variation
    WHERE is_default;

    SELECT count(*) INTO collection_item_count
    FROM public.menu_collection_item
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    SELECT count(*) INTO collection_category_count
    FROM public.menu_collection_category
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    IF menu_item_count <> 40 THEN
        RAISE EXCEPTION 'Expected 40 menu items after seed; found %', menu_item_count;
    END IF;

    IF default_variation_count <> 40 THEN
        RAISE EXCEPTION 'Expected 40 default variations after seed; found %',
            default_variation_count;
    END IF;

    IF collection_item_count <> 40 THEN
        RAISE EXCEPTION 'Expected 40 Main Menu memberships after seed; found %',
            collection_item_count;
    END IF;

    IF collection_category_count <> 7 THEN
        RAISE EXCEPTION 'Expected 7 Main Menu collection categories after seed; found %',
            collection_category_count;
    END IF;
END
$$;
