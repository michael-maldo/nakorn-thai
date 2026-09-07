-- V22__add_lunch_special_menu.sql
--
-- Phase 5 — Lunch Special
--
-- Adds a separate Lunch Special collection and L1-L10 printed lunch items.
-- Restaurant opening/closed-date rules remain owned by the Phase 4 restaurant
-- scheduling domain. This migration adds only a menu-specific daily cutoff:
--
--     Lunch Special allowed while restaurant is open AND local time < 14:30
--
-- The backend must compose restaurant availability with this menu cutoff.
-- No restaurant opening hours are duplicated here.
--
-- All prices are integer AUD minor units.
-- No allergens are inferred.
-- L1 source description is intentionally preserved as visibly truncated:
--     "Rice noodles with egg, tofu, bean sprouts and crushed"
--
-- L4 is interpreted narrowly from its individual printed label
-- "FRIED RICE CHICKEN/BEEF": Chicken or Beef only.
--
-- Deterministic UUIDs were generated from stable V22 seed names.

-- -------------------------------------------------------------------------
-- Precondition guards
-- -------------------------------------------------------------------------

DO $$
DECLARE
    main_menu_count integer;
    main_category_count integer;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.menu_collection
        WHERE id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73'
          AND slug = 'main-menu'
    ) THEN
        RAISE EXCEPTION 'V22 expects the V18/V20 Main Menu collection';
    END IF;

    SELECT count(*) INTO main_menu_count
    FROM public.menu_collection_item
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    SELECT count(*) INTO main_category_count
    FROM public.menu_collection_category
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    IF main_menu_count <> 82 THEN
        RAISE EXCEPTION
            'V22 expects 82 Main Menu memberships from V20; found %',
            main_menu_count;
    END IF;

    IF main_category_count <> 13 THEN
        RAISE EXCEPTION
            'V22 expects 13 Main Menu categories from V20; found %',
            main_category_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.dietary_tag
        WHERE id = 'b814e25a-44b2-5bb7-aa64-79b1f763bdee'
          AND code = 'GF'
    ) THEN
        RAISE EXCEPTION 'V22 expects the existing GF dietary tag';
    END IF;
END
$$;


-- -------------------------------------------------------------------------
-- Menu-specific daily cutoff
-- -------------------------------------------------------------------------
-- NULL means no daily cutoff.
-- A non-NULL value is interpreted in the authoritative Phase 4 restaurant timezone.
-- menu_collection.timezone continues to govern existing collection schedule rows.
-- The cutoff is end-exclusive:
--
--     local time < daily_cutoff_time  -> passes this rule
--     local time >= daily_cutoff_time -> unavailable
--
-- This is intentionally different from restaurant opening hours.
-- Restaurant hours and closed dates are evaluated separately by the
-- Phase 4 restaurant availability component.

ALTER TABLE public.menu_collection
    ADD COLUMN daily_cutoff_time time without time zone
        CHECK (daily_cutoff_time < TIME '24:00:00');

COMMENT ON COLUMN public.menu_collection.daily_cutoff_time IS
    'Optional menu-specific end-exclusive daily local-time cutoff; evaluated in the Phase 4 restaurant timezone. NULL means unrestricted by daily cutoff.';


-- -------------------------------------------------------------------------
-- Lunch Special collection
-- -------------------------------------------------------------------------

INSERT INTO public.menu_collection
    (id, name, slug, description, status, display_order,
     is_active, timezone, daily_cutoff_time)
VALUES
    (
        '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2',
        'Lunch Special',
        'lunch-special',
        'Nakorn Thai Lunch Special. Available while the restaurant is open and before 2:30 PM local restaurant time.',
        'PUBLISHED',
        2,
        true,
        'Australia/Melbourne',
        TIME '14:30:00'
    );


-- -------------------------------------------------------------------------
-- Lunch Special category
-- -------------------------------------------------------------------------

INSERT INTO public.menu_category
    (id, name, slug, description, display_order, is_active)
VALUES
    (
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'Lunch Special',
        'lunch-special',
        'Lunch Special menu category.',
        14,
        true
    );

INSERT INTO public.menu_collection_category
    (id, collection_id, category_id, display_order, version)
VALUES
    (
        '050b2cba-9229-574f-9981-6b100e3ba84d',
        '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        1,
        0
    );


-- -------------------------------------------------------------------------
-- Lunch Special canonical items
-- -------------------------------------------------------------------------
-- Lunch-specific canonical items are used intentionally so Lunch Special
-- descriptions/options do not leak into Main Menu item-global behavior.

INSERT INTO public.menu_item
    (id, category_id, name, slug, description, status, is_available,
     display_order, allergen_review_status, version)
VALUES
    (
        '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L1. Pad Thai',
        'lunch-l1-pad-thai',
        'Rice noodles with egg, tofu, bean sprouts and crushed',
        'PUBLISHED', true, 1, 'NOT_REVIEWED', 0
    ),
    (
        'b574b957-eff7-5123-8506-2d82f04a7a63',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L2. Pad See Eiw',
        'lunch-l2-pad-see-eiw',
        'Stir-fried flat noodles with egg and Chinese broccoli',
        'PUBLISHED', true, 2, 'NOT_REVIEWED', 0
    ),
    (
        'bee31c85-226b-5ee5-91d6-b2cab696317f',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L3. Pad Kee Mao',
        'lunch-l3-pad-kee-mao',
        'Stirfried thick rice noodles, vegetable, fresh chilli & Thai basil.',
        'PUBLISHED', true, 3, 'NOT_REVIEWED', 0
    ),
    (
        'f5c980c7-5273-527b-a7e2-6c7911c1d764',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L4. Fried Rice Chicken/Beef',
        'lunch-l4-fried-rice-chicken-beef',
        'Rice, egg, onion, bok choy, carrot and broccoli',
        'PUBLISHED', true, 4, 'NOT_REVIEWED', 0
    ),
    (
        '46060b9c-77f7-51a4-8016-0ab6a4252dca',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L5. Green Curry with Rice',
        'lunch-l5-green-curry-with-rice',
        'Pumpkin, green beans, zucchini, capsicum, bamboo and basil.',
        'PUBLISHED', true, 5, 'NOT_REVIEWED', 0
    ),
    (
        'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L6. Red Curry with Rice',
        'lunch-l6-red-curry-with-rice',
        'Pumpkin, green beans, zucchini, capsicum, carrot, bamboo and Basil',
        'PUBLISHED', true, 6, 'NOT_REVIEWED', 0
    ),
    (
        '7e031385-cffd-549c-953e-28319c3c08ed',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L7. Yellow Curry with Rice',
        'lunch-l7-yellow-curry-with-rice',
        'Potatoes, carrot, onion served with rice',
        'PUBLISHED', true, 7, 'NOT_REVIEWED', 0
    ),
    (
        '9d43014a-c13a-5366-a68b-f250e270d727',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L8. Pad Kra Pow with Rice',
        'lunch-l8-pad-kra-pow-with-rice',
        'Stir Fried with Garlic, Chilli, Onion, Capsicum, Zucchini, Carrot, Broccoli, Green Beans, Bamboo Shoot And Basil',
        'PUBLISHED', true, 8, 'NOT_REVIEWED', 0
    ),
    (
        'd4824913-6c99-56e2-aa53-db25c7109a96',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L9. Cashew Nut with Chilli Jam',
        'lunch-l9-cashew-nut-with-chilli-jam',
        'Onion, Capsicum, Zucchini, Broccoli, Cashew Nut and Spring onion Served with Rice',
        'PUBLISHED', true, 9, 'NOT_REVIEWED', 0
    ),
    (
        'f131d93b-3b20-5403-8c2e-1b209507ab6b',
        'eaafe867-4a8f-50b3-be17-7285a7190383',
        'L10. Ginger Stir Fried with Rice',
        'lunch-l10-ginger-stir-fried-with-rice',
        'Broccoli, Carrot, Zucchini, Snow Peas, Garlic, Onion and Black Pepper Served with Rice',
        'PUBLISHED', true, 10, 'NOT_REVIEWED', 0
    );


-- -------------------------------------------------------------------------
-- Base/default variations — all printed base prices are $14.90
-- -------------------------------------------------------------------------

INSERT INTO public.menu_item_variation
    (id, menu_item_id, name, sku, price_minor, currency, is_default,
     is_active, is_available, display_order, allergen_review_status, version)
VALUES
    ('6e78deed-b9bc-537a-8a08-fc7e0eafbf62', '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('43520b32-86c9-5004-909c-f48ecfdc756a', 'b574b957-eff7-5123-8506-2d82f04a7a63', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('77584d9b-aa19-527e-aa03-39e80a25f39c', 'bee31c85-226b-5ee5-91d6-b2cab696317f', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f12c1761-54fa-5393-97a7-a373c5c0b423', 'f5c980c7-5273-527b-a7e2-6c7911c1d764', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('ea6b546e-5efe-5be6-abde-925412377abc', '46060b9c-77f7-51a4-8016-0ab6a4252dca', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('53c59bef-8c27-5a79-9944-fe26862c425e', 'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f47d5ada-3146-531e-8228-645cdcde264b', '7e031385-cffd-549c-953e-28319c3c08ed', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('2b62c915-e51b-5654-95d1-f9921032d23f', '9d43014a-c13a-5366-a68b-f250e270d727', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('666f4a12-70ae-5206-b5a5-50b9e92a4d5d', 'd4824913-6c99-56e2-aa53-db25c7109a96', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('a7711d8a-f067-5f7e-a555-3c8482b36513', 'f131d93b-3b20-5403-8c2e-1b209507ab6b', 'Standard', NULL, 1490, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0);


-- -------------------------------------------------------------------------
-- Collection memberships
-- -------------------------------------------------------------------------

INSERT INTO public.menu_collection_item
    (collection_id, menu_item_id, collection_category_id, display_order,
     price_override_minor, version)
VALUES
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a', '050b2cba-9229-574f-9981-6b100e3ba84d', 1, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'b574b957-eff7-5123-8506-2d82f04a7a63', '050b2cba-9229-574f-9981-6b100e3ba84d', 2, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'bee31c85-226b-5ee5-91d6-b2cab696317f', '050b2cba-9229-574f-9981-6b100e3ba84d', 3, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'f5c980c7-5273-527b-a7e2-6c7911c1d764', '050b2cba-9229-574f-9981-6b100e3ba84d', 4, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', '46060b9c-77f7-51a4-8016-0ab6a4252dca', '050b2cba-9229-574f-9981-6b100e3ba84d', 5, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5', '050b2cba-9229-574f-9981-6b100e3ba84d', 6, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', '7e031385-cffd-549c-953e-28319c3c08ed', '050b2cba-9229-574f-9981-6b100e3ba84d', 7, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', '9d43014a-c13a-5366-a68b-f250e270d727', '050b2cba-9229-574f-9981-6b100e3ba84d', 8, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'd4824913-6c99-56e2-aa53-db25c7109a96', '050b2cba-9229-574f-9981-6b100e3ba84d', 9, NULL, 0),
    ('8ed50da5-f6d9-54b3-9611-2bc33b7e54d2', 'f131d93b-3b20-5403-8c2e-1b209507ab6b', '050b2cba-9229-574f-9981-6b100e3ba84d', 10, NULL, 0);


-- -------------------------------------------------------------------------
-- GF dietary markings from printed Lunch Special source
-- L4, L5, L6, L7 only.
-- -------------------------------------------------------------------------

INSERT INTO public.menu_item_dietary_tag
    (menu_item_id, dietary_tag_id, notes, verified_at, version)
VALUES
    ('f5c980c7-5273-527b-a7e2-6c7911c1d764', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('46060b9c-77f7-51a4-8016-0ab6a4252dca', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('c6ab7d74-e60f-54d5-b802-8b7ec15abbd5', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0),
    ('7e031385-cffd-549c-953e-28319c3c08ed', 'b814e25a-44b2-5bb7-aa64-79b1f763bdee', NULL, NULL, 0);


-- -------------------------------------------------------------------------
-- Protein option groups
-- -------------------------------------------------------------------------
-- General printed Lunch Special choices:
--   Beef +0
--   Chicken +0
--   Veg & Tofu +0
--   Prawns +600
--   Seafood +800
--   Crispy Pork +500
--   Fish +600
--
-- L4 is intentionally narrower: Chicken / Beef only.

INSERT INTO public.menu_option_group
    (id, code, name, selection_type, is_active, version)
VALUES
    ('3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'lunch-protein', 'Protein', 'SINGLE', true, 0),
    ('0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8', 'lunch-fried-rice-protein', 'Protein', 'SINGLE', true, 0);

INSERT INTO public.menu_option
    (id, option_group_id, code, name, price_delta_minor, currency,
     display_order, is_active, version)
VALUES
    ('b1930f47-01fb-5ed2-80d2-8ccf9e4b36c9', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'beef', 'Beef', 0, 'AUD', 1, true, 0),
    ('ae2fd9a0-4245-52c1-93de-2f1d3a93d72c', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'chicken', 'Chicken', 0, 'AUD', 2, true, 0),
    ('8d87f6b4-b44f-54a7-b111-ec1e94c01333', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'veg-tofu', 'Veg & Tofu', 0, 'AUD', 3, true, 0),
    ('a9938cdf-241d-5a0d-8009-42f7c5cc385f', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'prawns', 'Prawns', 600, 'AUD', 4, true, 0),
    ('0786c4d4-200d-5d81-a2e2-a3f4601d77df', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'seafood', 'Seafood', 800, 'AUD', 5, true, 0),
    ('b9fbe7af-c215-5e66-8d34-185fec2e4390', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'crispy-pork', 'Crispy Pork', 500, 'AUD', 6, true, 0),
    ('5161eb48-f0aa-51f4-90a7-a9315c661d0e', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 'fish', 'Fish', 600, 'AUD', 7, true, 0),

    ('08dea533-80ae-5e18-8a00-5ba091a542e7', '0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8', 'chicken', 'Chicken', 0, 'AUD', 1, true, 0),
    ('d9839710-c421-58b9-a51d-ee15a6183e87', '0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8', 'beef', 'Beef', 0, 'AUD', 2, true, 0);


-- -------------------------------------------------------------------------
-- Required SINGLE protein selection
-- -------------------------------------------------------------------------
-- General lunch protein applies to L1-L3 and L5-L10.
-- L4 uses its narrow Chicken/Beef group.

INSERT INTO public.menu_item_option_group
    (menu_item_id, option_group_id, min_selections, max_selections,
     display_order, version)
VALUES
    ('2f02a1b7-3d12-5aa6-a039-6d22670e2c2a', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('b574b957-eff7-5123-8506-2d82f04a7a63', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('bee31c85-226b-5ee5-91d6-b2cab696317f', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),

    ('f5c980c7-5273-527b-a7e2-6c7911c1d764', '0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8', 1, 1, 1, 0),

    ('46060b9c-77f7-51a4-8016-0ab6a4252dca', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('c6ab7d74-e60f-54d5-b802-8b7ec15abbd5', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('7e031385-cffd-549c-953e-28319c3c08ed', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('9d43014a-c13a-5366-a68b-f250e270d727', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('d4824913-6c99-56e2-aa53-db25c7109a96', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0),
    ('f131d93b-3b20-5403-8c2e-1b209507ab6b', '3ea6a4fe-f4ac-55fd-b846-e293cfd36687', 1, 1, 1, 0);


-- -------------------------------------------------------------------------
-- Verification guards
-- -------------------------------------------------------------------------

DO $$
DECLARE
    lunch_collection_count integer;
    lunch_category_count integer;
    lunch_membership_count integer;
    lunch_item_count integer;
    lunch_variation_count integer;
    lunch_gf_count integer;
    lunch_option_group_count integer;
    lunch_option_count integer;
    lunch_attachment_count integer;
BEGIN
    SELECT count(*) INTO lunch_collection_count
    FROM public.menu_collection
    WHERE id = '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2'
      AND slug = 'lunch-special'
      AND status = 'PUBLISHED'
      AND is_active = true
      AND timezone = 'Australia/Melbourne'
      AND daily_cutoff_time = TIME '14:30:00';

    SELECT count(*) INTO lunch_category_count
    FROM public.menu_collection_category
    WHERE id = '050b2cba-9229-574f-9981-6b100e3ba84d'
      AND collection_id = '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2'
      AND category_id = 'eaafe867-4a8f-50b3-be17-7285a7190383';

    SELECT count(*) INTO lunch_membership_count
    FROM public.menu_collection_item
    WHERE collection_id = '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2';

    SELECT count(*) INTO lunch_item_count
    FROM public.menu_item
    WHERE id IN (
        '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a',
        'b574b957-eff7-5123-8506-2d82f04a7a63',
        'bee31c85-226b-5ee5-91d6-b2cab696317f',
        'f5c980c7-5273-527b-a7e2-6c7911c1d764',
        '46060b9c-77f7-51a4-8016-0ab6a4252dca',
        'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5',
        '7e031385-cffd-549c-953e-28319c3c08ed',
        '9d43014a-c13a-5366-a68b-f250e270d727',
        'd4824913-6c99-56e2-aa53-db25c7109a96',
        'f131d93b-3b20-5403-8c2e-1b209507ab6b'
    );

    SELECT count(*) INTO lunch_variation_count
    FROM public.menu_item_variation
    WHERE menu_item_id IN (
        '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a',
        'b574b957-eff7-5123-8506-2d82f04a7a63',
        'bee31c85-226b-5ee5-91d6-b2cab696317f',
        'f5c980c7-5273-527b-a7e2-6c7911c1d764',
        '46060b9c-77f7-51a4-8016-0ab6a4252dca',
        'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5',
        '7e031385-cffd-549c-953e-28319c3c08ed',
        '9d43014a-c13a-5366-a68b-f250e270d727',
        'd4824913-6c99-56e2-aa53-db25c7109a96',
        'f131d93b-3b20-5403-8c2e-1b209507ab6b'
    )
      AND is_default = true
      AND price_minor = 1490
      AND currency = 'AUD';

    SELECT count(*) INTO lunch_gf_count
    FROM public.menu_item_dietary_tag
    WHERE dietary_tag_id = 'b814e25a-44b2-5bb7-aa64-79b1f763bdee'
      AND menu_item_id IN (
        'f5c980c7-5273-527b-a7e2-6c7911c1d764',
        '46060b9c-77f7-51a4-8016-0ab6a4252dca',
        'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5',
        '7e031385-cffd-549c-953e-28319c3c08ed'
      );

    SELECT count(*) INTO lunch_option_group_count
    FROM public.menu_option_group
    WHERE id IN (
        '3ea6a4fe-f4ac-55fd-b846-e293cfd36687',
        '0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8'
    );

    SELECT count(*) INTO lunch_option_count
    FROM public.menu_option
    WHERE option_group_id IN (
        '3ea6a4fe-f4ac-55fd-b846-e293cfd36687',
        '0f71b61d-9007-5e4f-997b-7d3fbcfaa4b8'
    );

    SELECT count(*) INTO lunch_attachment_count
    FROM public.menu_item_option_group
    WHERE menu_item_id IN (
        '2f02a1b7-3d12-5aa6-a039-6d22670e2c2a',
        'b574b957-eff7-5123-8506-2d82f04a7a63',
        'bee31c85-226b-5ee5-91d6-b2cab696317f',
        'f5c980c7-5273-527b-a7e2-6c7911c1d764',
        '46060b9c-77f7-51a4-8016-0ab6a4252dca',
        'c6ab7d74-e60f-54d5-b802-8b7ec15abbd5',
        '7e031385-cffd-549c-953e-28319c3c08ed',
        '9d43014a-c13a-5366-a68b-f250e270d727',
        'd4824913-6c99-56e2-aa53-db25c7109a96',
        'f131d93b-3b20-5403-8c2e-1b209507ab6b'
    )
      AND min_selections = 1
      AND max_selections = 1;

    IF lunch_collection_count <> 1 THEN
        RAISE EXCEPTION 'Expected one valid Lunch Special collection; found %',
            lunch_collection_count;
    END IF;

    IF lunch_category_count <> 1 THEN
        RAISE EXCEPTION 'Expected one Lunch Special collection category; found %',
            lunch_category_count;
    END IF;

    IF lunch_membership_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 Lunch Special memberships; found %',
            lunch_membership_count;
    END IF;

    IF lunch_item_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 Lunch Special items; found %',
            lunch_item_count;
    END IF;

    IF lunch_variation_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 $14.90 default Lunch Special variations; found %',
            lunch_variation_count;
    END IF;

    IF lunch_gf_count <> 4 THEN
        RAISE EXCEPTION 'Expected 4 GF Lunch Special item mappings; found %',
            lunch_gf_count;
    END IF;

    IF lunch_option_group_count <> 2 THEN
        RAISE EXCEPTION 'Expected 2 Lunch Special protein groups; found %',
            lunch_option_group_count;
    END IF;

    IF lunch_option_count <> 9 THEN
        RAISE EXCEPTION 'Expected 9 Lunch Special protein options; found %',
            lunch_option_count;
    END IF;

    IF lunch_attachment_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 required Lunch Special protein attachments; found %',
            lunch_attachment_count;
    END IF;
END
$$;
