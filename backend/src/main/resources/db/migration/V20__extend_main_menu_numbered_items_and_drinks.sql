-- V20__extend_main_menu.sql
--
-- Continues the V18 Main Menu seed.
--
-- Adds:
-- * printed Main Menu food items 41-58;
-- * Noodle Soup, Soup, Salad, Sides, Dessert and Drinks categories;
-- * individually orderable drink items;
-- * Sparkling Water Small/Large variations;
-- * Soup and Chicken/Beef Salad choice groups;
-- * printed item numbers as part of menu_item.name for existing Main Menu
--   food items and new food items.
--
-- Important:
-- * The printed source has no item 29; numbering goes 28 -> 30.
-- * Fish includes both 14 and 14A.
-- * Drinks are intentionally unnumbered because the printed Drinks section
--   is unnumbered.
-- * This migration is additive and does not reset order/staff/reservation data.
-- * menu_item.name is canonical in the current schema. Therefore numbered
--   names follow an item if that item is reused by another collection.
-- * No allergens are inferred.


-- Menu descriptions are optional. Some items (for example rice, soft drinks,
-- and bottled beverages) do not need descriptive copy.
ALTER TABLE menu_item
    DROP CONSTRAINT IF EXISTS menu_item_description_check;

ALTER TABLE menu_item
    ALTER COLUMN description DROP NOT NULL;

DO $$
DECLARE
    main_menu_count integer;
    main_category_count integer;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.menu_collection
        WHERE id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73'
          AND slug = 'main-menu'
    ) THEN
        RAISE EXCEPTION 'V20 expects the V18 Main Menu collection';
    END IF;

    SELECT count(*) INTO main_menu_count
    FROM public.menu_collection_item
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    SELECT count(*) INTO main_category_count
    FROM public.menu_collection_category
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    IF main_menu_count <> 40 THEN
        RAISE EXCEPTION 'V20 expects 40 Main Menu memberships; found %', main_menu_count;
    END IF;
    IF main_category_count <> 7 THEN
        RAISE EXCEPTION 'V20 expects 7 Main Menu categories; found %', main_category_count;
    END IF;
END
$$;

-- Existing V18 items: make printed menu number part of canonical name.
UPDATE public.menu_item AS mi
SET name = v.name
FROM (VALUES
    ('34126363-fbb5-569a-afcd-46daaaf95119'::uuid, '1. Prawn Crackers with Chilli Jam'),
    ('ff11db97-2992-5b75-9784-81956be02321'::uuid, '2. Roti (2 Pieces)'),
    ('cc3991d8-4e34-5dc6-9a7c-f2f03a61e712'::uuid, '3. Chicken Spring Rolls (4 Pieces)'),
    ('7dec9482-b162-544e-8c6b-cebb8b6417f3'::uuid, '4. Vegetable Spring Rolls (4 Pieces)'),
    ('fe42722b-468b-55b9-b4bf-b29175672448'::uuid, '5. Vegetable Curry Puffs (4 Pieces)'),
    ('8e1555ab-ef0f-562b-a159-b7a7fd7774db'::uuid, '6. Fish Cakes (4 Pieces)'),
    ('8964f5e5-f863-5a7b-9004-5315d2f7e486'::uuid, '7. Salt & Pepper Calamari'),
    ('e051f03f-3341-54e6-9d77-9037d2520b5b'::uuid, '8. Calamari Rings'),
    ('05e94fae-4924-59e3-a432-abf694ba5838'::uuid, '9. Tempura Prawns'),
    ('01bf8852-06cc-5418-ac53-8a1301d84faf'::uuid, '10. Satay Chicken (4 Skewers)'),
    ('1608f145-0f18-55d2-af0f-82e913c3f29f'::uuid, '11. Vegetable San Choy Bow'),
    ('659b5509-f838-5891-9791-07c40ca8140c'::uuid, '12. Chicken Pandan'),
    ('f90fa713-8d21-5b37-8b38-94690c81f018'::uuid, '13. Mix Platte'),
    ('793ed136-35c9-530d-834e-fa2eff2ffeaa'::uuid, '14. Whole Barramundi Fish'),
    ('3594448a-54a5-59c9-9f51-e558d3d90eed'::uuid, '14A. Fish Fillets'),
    ('2576add4-cce5-5635-965d-bed257c12ac7'::uuid, '15. Green Curry'),
    ('49edf648-f45e-5d3b-be63-47e1c1193d55'::uuid, '16. Red Curry'),
    ('aa6fe0ee-fecc-5c8b-acac-056a511f12f3'::uuid, '17. Yellow Curry'),
    ('3e07bb88-81b6-55fb-8719-8b81f5c751c3'::uuid, '18. Panang Curry'),
    ('bc890114-3190-564f-8e9a-583db0a1e599'::uuid, '19. Massaman Beef'),
    ('4da014ef-dd26-59d5-a095-f21eb2e0cef0'::uuid, '20. Jungle Curry'),
    ('8e57d123-23b4-5e89-bd3f-d6d54fb2a8c9'::uuid, '21. Cashew Nuts with Chilli Jam'),
    ('4b094692-84e8-5391-a1cd-b8bd5a9ad56e'::uuid, '22. Sweet and Sour (Pad Priew Wang)'),
    ('3e1467b3-5b3e-58de-b556-ad6b2383efc9'::uuid, '23. Fresh Ginger (Pad Khing)'),
    ('ad54fef3-93d2-5063-aa11-ae6f71710ec5'::uuid, '24. Pad Krapao'),
    ('7d4da000-020e-5d80-aec2-674e091e213f'::uuid, '25. Garlic and Pepper'),
    ('e7b3fc56-6721-5ca7-89f8-546c080ac685'::uuid, '26. Satay Stir-Fried'),
    ('fb37d31e-c543-51f8-aca1-7deaa4990d89'::uuid, '27. Stir-Fried Basil with Eggplant'),
    ('ac162e2d-fbff-5ed9-b58a-45299595ae94'::uuid, '28. Zucchini Tofu Platter'),
    ('8d411e9f-2847-5008-9228-f4757d83cbb0'::uuid, '30. Grilled Marinated Chicken'),
    ('a1ff7e60-297d-5416-885d-c86228c6c329'::uuid, '31. Grilled Marinated Pork'),
    ('ad4f1fe2-f094-5861-a759-b48b29882e1d'::uuid, '32. Grilled Marinated Beef - 350G'),
    ('88698b0e-72d3-54a3-8fb7-7645d6e09e13'::uuid, '33. Pad Thai'),
    ('fc73b860-2475-5d4c-bbd9-1e27f8872f2c'::uuid, '34. Pad See Ew'),
    ('ffd46610-0704-5e7c-91cf-fa02db4ad1af'::uuid, '35. Pad Kee Mao'),
    ('f454ac07-8670-56f2-92b1-beb4da66fe14'::uuid, '36. Egg Noodles with BBQ Chicken'),
    ('bbb36e05-9ec7-551a-a0a4-1417c1a59693'::uuid, '37. Fried Rice'),
    ('d6413c22-8d4b-5faf-b088-1874bcee0199'::uuid, '38. Chilli Fried Rice'),
    ('151c26bf-c14a-5842-aca7-8d3ee15124ab'::uuid, '39. Tom Yum Fried Rice'),
    ('3977c2d9-d02b-5895-8457-31821795f5d5'::uuid, '40. Pineapple Fried Rice')
) AS v(id, name)
WHERE mi.id = v.id;

-- New categories.
INSERT INTO public.menu_category
    (id, name, slug, description, display_order, is_active)
VALUES
    ('4e90bbad-4508-526d-9499-5e5be4361657', 'Noodle Soup', 'noodle-soup', 'Main menu category.', 8, true),
    ('9226628c-5f8a-5de2-8c47-f67f119c7e7b', 'Soup', 'soup', 'Main menu category.', 9, true),
    ('36fd5ebb-60f8-5bec-b986-822606e4df97', 'Salad', 'salad', 'Main menu category.', 10, true),
    ('38522d39-3c86-5eb6-9b3d-185878b2fecd', 'Sides', 'sides', 'Main menu category.', 11, true),
    ('1e53c0df-5676-5a76-9ac6-7551b1b46a0b', 'Dessert', 'dessert', 'Main menu category.', 12, true),
    ('68215a2d-d635-524a-ac83-33b1d30b6736', 'Drinks', 'drinks', 'Main menu category.', 13, true);

INSERT INTO public.menu_collection_category
    (id, collection_id, category_id, display_order, version)
VALUES
    ('7342f934-08e2-5993-8c2e-7143808eec86', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '4e90bbad-4508-526d-9499-5e5be4361657', 8, 0),
    ('3571dea4-fb14-5a68-b2ec-2a3df8be4d74', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '9226628c-5f8a-5de2-8c47-f67f119c7e7b', 9, 0),
    ('c112f549-a7b8-54aa-9aed-0e0051e9812f', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '36fd5ebb-60f8-5bec-b986-822606e4df97', 10, 0),
    ('a5329912-01b1-5c08-bf01-c478ed5f48f5', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '38522d39-3c86-5eb6-9b3d-185878b2fecd', 11, 0),
    ('7920180c-5400-526b-a0de-e5cb71eb569f', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', 12, 0),
    ('d9e1be1d-6029-5610-9fd6-0ec4195a91f8', 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '68215a2d-d635-524a-ac83-33b1d30b6736', 13, 0);

INSERT INTO public.dietary_tag
    (id, code, name, description, display_order, is_active)
VALUES
    ('0a437e77-0441-5b51-901e-af2077d269ca', 'VG', 'Vegan', 'Vegan as indicated on the supplied printed menu.', 2, true),
    ('19a270a5-a989-59c4-adc5-09d5264be463', 'V', 'Vegetarian', 'Vegetarian as indicated on the supplied printed menu.', 3, true);

-- New food + drink menu items.
INSERT INTO public.menu_item
    (id, category_id, name, slug, description, status, is_available,
     display_order, allergen_review_status, version)
VALUES
    ('21f4de0c-44b5-5a84-92b7-52fa491b0a90', '4e90bbad-4508-526d-9499-5e5be4361657', '41. Chicken Noodle Soup', 'chicken-noodle-soup', 'Chicken with bean sprouts, Chinese broccoli and rice noodles on top with garnish.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('a3ccef91-4a88-5283-9a97-cfd46af73b98', '4e90bbad-4508-526d-9499-5e5be4361657', '42. Tom Yum Seafood Noodles Soup', 'tom-yum-seafood-noodles-soup', 'Mixed seafood in Tom Yum soup with bean sprouts, mushroom and rice noodles on top with garnish.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('08ff1fa5-54aa-5e16-b307-730d0217d2d5', '9226628c-5f8a-5de2-8c47-f67f119c7e7b', '43. Tom Yum Soup', 'tom-yum-soup', 'Hot and sour soup flavoured with chopped galangal, lemon grass, kaffir lime leaf, lemon juice, onion, tomato and mushroom.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('854558d7-399d-5694-acbe-53a52d5545b3', '9226628c-5f8a-5de2-8c47-f67f119c7e7b', '44. Tom Kha Kai', 'tom-kha-kai', 'Light coconut milk soup flavoured with galangal, lemon grass, kaffir lime leaf, lemon juice, chilli, onion, tomato and mushroom.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('d26a641a-dfd4-5d09-acb3-1c2ddd5c3575', '36fd5ebb-60f8-5bec-b986-822606e4df97', '45. Chicken / Beef Salad', 'chicken-beef-salad', 'BBQ chicken or slices beef with fresh lime, chilli, tomato, cucumber, onion, coriander and spring onion.', 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('4b097ea6-8f08-5f94-92a8-20b599042aa9', '36fd5ebb-60f8-5bec-b986-822606e4df97', '46. Deep-Fried Tofu Salad', 'deep-fried-tofu-salad', 'Deep-fried tofu with fresh lime, chilli, tomato, cucumber, onion, coriander and spring onion.', 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('aba29472-15e1-5f53-954b-3503c4b1083c', '36fd5ebb-60f8-5bec-b986-822606e4df97', '47. Larb Salad', 'larb-salad', 'Chicken minced with chilli flakes, fresh lemon juice, red onion, mints, coriander and grounded roasted rice.', 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('1fef33c0-34e3-5d86-b522-b26cb0a2b927', '38522d39-3c86-5eb6-9b3d-185878b2fecd', '48. Coconut Rice', 'coconut-rice', NULL, 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('0ac4afbd-1d96-5411-87a3-e79e77c97463', '38522d39-3c86-5eb6-9b3d-185878b2fecd', '49. Steamed Rice', 'steamed-rice', NULL, 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('b4e8b599-a6f2-5e0d-adb0-c9054e3226a0', '38522d39-3c86-5eb6-9b3d-185878b2fecd', '50. Roti', 'roti', NULL, 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('d6e7b5a5-abc2-50ba-ace1-da60f0ef4f29', '38522d39-3c86-5eb6-9b3d-185878b2fecd', '51. Home Made Peanut Sauce', 'home-made-peanut-sauce', NULL, 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('f809c3c5-2d28-57ad-b728-598de99c84bc', '38522d39-3c86-5eb6-9b3d-185878b2fecd', '52. Steamed Mixed Vegetables', 'steamed-mixed-vegetables', NULL, 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('b1584052-806a-5acc-8000-2b19f96770ae', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '53. Banana Dumplings', 'banana-dumplings', NULL, 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('3d53e079-a07b-5543-a681-5cade6b2679a', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '54. Kanom Tuay', 'kanom-tuay', NULL, 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('07ed25fe-df00-5d1e-9d98-be7cca0f7bfd', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '55. Banana Fritter', 'banana-fritter', NULL, 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('34c12338-b592-5c47-a808-345938a5f2a7', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '56. Taro Dumpling', 'taro-dumpling', NULL, 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('25ee2f8a-7493-5108-9fc8-e884ef28dc9d', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '57. Sticky Date Pudding', 'sticky-date-pudding', NULL, 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('8cbfb9a2-a854-5e3b-811b-4cd2e474826a', '1e53c0df-5676-5a76-9ac6-7551b1b46a0b', '58. Mixed Berries Crepes', 'mixed-berries-crepes', NULL, 'PUBLISHED', true, 6, 'NOT_REVIEWED', 0),
    ('ad652f75-c1a3-5fde-abb6-39b67022f3ca', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Ice Black Coffee', 'ice-black-coffee', NULL, 'PUBLISHED', true, 1, 'NOT_REVIEWED', 0),
    ('24941f25-fe8b-5408-a2ec-3e950ed3276c', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Milk Ice Coffee', 'milk-ice-coffee', NULL, 'PUBLISHED', true, 2, 'NOT_REVIEWED', 0),
    ('610d8b1b-41f2-56b7-b70a-d40d0bf538ba', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Thai Milk Tea', 'thai-milk-tea', NULL, 'PUBLISHED', true, 3, 'NOT_REVIEWED', 0),
    ('0742c5ed-65ef-5005-a618-fa7e12c2f1f6', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Ice Lemon Tea', 'ice-lemon-tea', NULL, 'PUBLISHED', true, 4, 'NOT_REVIEWED', 0),
    ('c5639f69-d3a3-5884-9c20-2ae939a683a2', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Apple Juice', 'apple-juice', NULL, 'PUBLISHED', true, 5, 'NOT_REVIEWED', 0),
    ('44334022-fa29-5f30-9c76-80986f8e42cc', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Orange Juice', 'orange-juice', NULL, 'PUBLISHED', true, 6, 'NOT_REVIEWED', 0),
    ('1304484d-6ae8-5b61-8a9d-d3f7e0d50850', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Pomeapple Juice', 'pomeapple-juice', NULL, 'PUBLISHED', true, 7, 'NOT_REVIEWED', 0),
    ('07259ce8-f781-5fd9-8c95-59bd0073bf87', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Cranberry Juice', 'cranberry-juice', NULL, 'PUBLISHED', true, 8, 'NOT_REVIEWED', 0),
    ('3ad4f093-8bc7-5971-b50a-b5260c56d21b', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Tomato Juice', 'tomato-juice', NULL, 'PUBLISHED', true, 9, 'NOT_REVIEWED', 0),
    ('6ba83645-542a-50de-ace7-5dc80e0ad21c', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Coke', 'coke', NULL, 'PUBLISHED', true, 10, 'NOT_REVIEWED', 0),
    ('87fb719f-7a1a-5f9b-a018-2493d3146d07', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Coke Zero', 'coke-zero', NULL, 'PUBLISHED', true, 11, 'NOT_REVIEWED', 0),
    ('45118c45-762a-5a0f-901c-ffa25646c074', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Fanta', 'fanta', NULL, 'PUBLISHED', true, 12, 'NOT_REVIEWED', 0),
    ('52a65fce-1c26-5bcf-980a-6edc78d79af8', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Lemonade', 'lemonade', NULL, 'PUBLISHED', true, 13, 'NOT_REVIEWED', 0),
    ('5ef34c68-75f3-5b13-a472-a900d7c3e72b', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Sprite', 'sprite', NULL, 'PUBLISHED', true, 14, 'NOT_REVIEWED', 0),
    ('370c9e26-8edd-5a59-bd60-fa3f25777625', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Lemon Squash', 'lemon-squash', NULL, 'PUBLISHED', true, 15, 'NOT_REVIEWED', 0),
    ('81449705-2ad0-5180-bb5f-1efa41c713f3', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Ginger Beer', 'ginger-beer', NULL, 'PUBLISHED', true, 16, 'NOT_REVIEWED', 0),
    ('d8e384ea-176b-5e08-a8a9-c422e0a6d132', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Pepsi', 'pepsi', NULL, 'PUBLISHED', true, 17, 'NOT_REVIEWED', 0),
    ('d696bb98-861d-575c-9ac0-263ca97d3c86', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Pepsi Max', 'pepsi-max', NULL, 'PUBLISHED', true, 18, 'NOT_REVIEWED', 0),
    ('55909e23-6eae-5237-b1a6-4fe831cfcad6', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Solo', 'solo', NULL, 'PUBLISHED', true, 19, 'NOT_REVIEWED', 0),
    ('4809bdf6-a358-5796-b001-ea405092c083', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Pasito', 'pasito', NULL, 'PUBLISHED', true, 20, 'NOT_REVIEWED', 0),
    ('204d0c14-9cb6-5344-b672-70584f41f352', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Sunkist', 'sunkist', NULL, 'PUBLISHED', true, 21, 'NOT_REVIEWED', 0),
    ('6f5513ef-6d26-5c51-9194-e9b5bc982b1d', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Diet Coke', 'diet-coke', NULL, 'PUBLISHED', true, 22, 'NOT_REVIEWED', 0),
    ('d13b257e-d923-55dd-aad4-ddb51163cf1b', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Sparkling Water', 'sparkling-water', NULL, 'PUBLISHED', true, 23, 'NOT_REVIEWED', 0),
    ('ae51c989-3304-52fe-8748-e688984a1bbc', '68215a2d-d635-524a-ac83-33b1d30b6736', 'Coconut Water', 'coconut-water', NULL, 'PUBLISHED', true, 24, 'NOT_REVIEWED', 0);

INSERT INTO public.menu_item_variation
    (id, menu_item_id, name, sku, price_minor, currency, is_default,
     is_active, is_available, display_order, allergen_review_status, version)
VALUES
    ('58d1608a-8de8-517c-ad13-6d13f22b6bf2', '21f4de0c-44b5-5a84-92b7-52fa491b0a90', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('2219555a-3a68-5cf4-bdb5-7672d26379e4', 'a3ccef91-4a88-5283-9a97-cfd46af73b98', 'Standard', NULL, 2590, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('36033ea9-fb90-5afa-b439-806f2c15c6a2', '08ff1fa5-54aa-5e16-b307-730d0217d2d5', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('bdc8a13a-e9bd-51b7-88db-28ffa57628a3', '854558d7-399d-5694-acbe-53a52d5545b3', 'Standard', NULL, 2090, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('482e39b5-3e08-5556-b78b-b003b32cc0cf', 'd26a641a-dfd4-5d09-acb3-1c2ddd5c3575', 'Standard', NULL, 2190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('7c9ea35e-9c34-593e-8db6-023e886b2564', '4b097ea6-8f08-5f94-92a8-20b599042aa9', 'Standard', NULL, 1890, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('19c2433d-6f0f-56dc-8d51-5bf63b16b85e', 'aba29472-15e1-5f53-954b-3503c4b1083c', 'Standard', NULL, 2190, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('0604cde5-44c1-5a04-9b15-7fa383837d94', '1fef33c0-34e3-5d86-b522-b26cb0a2b927', 'Standard', NULL, 750, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('204d1713-edb5-5c73-acaf-3b1be1e1a3cf', '0ac4afbd-1d96-5411-87a3-e79e77c97463', 'Standard', NULL, 650, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('5a948759-ae8a-512a-985a-5195d7e17e40', 'b4e8b599-a6f2-5e0d-adb0-c9054e3226a0', 'Standard', NULL, 380, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('4320db83-d393-569b-9448-8c78915f32a8', 'd6e7b5a5-abc2-50ba-ace1-da60f0ef4f29', 'Standard', NULL, 380, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('3a84bf9a-1c40-5aa7-ae20-f27a708ec376', 'f809c3c5-2d28-57ad-b728-598de99c84bc', 'Standard', NULL, 790, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('93b3515c-37f1-59a6-a9e3-e27bf3767720', 'b1584052-806a-5acc-8000-2b19f96770ae', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('40021908-556b-5d0f-b279-b4e384528b83', '3d53e079-a07b-5543-a681-5cade6b2679a', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('7a54f5fd-7ba7-5e92-bacc-e1a41d37d8b1', '07ed25fe-df00-5d1e-9d98-be7cca0f7bfd', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('ea158f9a-37f4-56cb-8be2-563219a5fb6f', '34c12338-b592-5c47-a808-345938a5f2a7', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('56bd35a2-b544-529f-acb8-63670a15edcd', '25ee2f8a-7493-5108-9fc8-e884ef28dc9d', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('2830dea1-4119-5356-938a-a9ad31df656d', '8cbfb9a2-a854-5e3b-811b-4cd2e474826a', 'Standard', NULL, 1290, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('feb05576-4f69-533a-b666-c876aea4fe6f', 'ad652f75-c1a3-5fde-abb6-39b67022f3ca', 'Standard', NULL, 999, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('b0fbf653-5702-5507-befa-6d44c45481a4', '24941f25-fe8b-5408-a2ec-3e950ed3276c', 'Standard', NULL, 999, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('3d4049c6-ae1a-5bbe-bc31-267a52db3931', '610d8b1b-41f2-56b7-b70a-d40d0bf538ba', 'Standard', NULL, 999, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('b7781aa9-9c56-5054-a61b-0e8fc98ff523', '0742c5ed-65ef-5005-a618-fa7e12c2f1f6', 'Standard', NULL, 999, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f4f0d758-fb4f-53eb-a8e9-9fd9f82a03e8', 'c5639f69-d3a3-5884-9c20-2ae939a683a2', 'Standard', NULL, 499, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('9ae61b45-0907-53f7-a094-df7b1904f320', '44334022-fa29-5f30-9c76-80986f8e42cc', 'Standard', NULL, 499, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('08e40e9a-05d3-5255-a528-5c63f48722ce', '1304484d-6ae8-5b61-8a9d-d3f7e0d50850', 'Standard', NULL, 499, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('d4d6127d-001f-5ba3-88a7-1f95cf29010a', '07259ce8-f781-5fd9-8c95-59bd0073bf87', 'Standard', NULL, 499, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('8e3a7aaa-84be-57a5-a48c-07659726d90e', '3ad4f093-8bc7-5971-b50a-b5260c56d21b', 'Standard', NULL, 499, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('a0d262f0-65c2-5b47-bbcf-06b5fe00fe5b', '6ba83645-542a-50de-ace7-5dc80e0ad21c', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('277db9de-c02f-5429-bd10-466b63aa9c30', '87fb719f-7a1a-5f9b-a018-2493d3146d07', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('c186d753-d4d4-54ac-b936-b83786e54bf5', '45118c45-762a-5a0f-901c-ffa25646c074', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f88da505-1a58-5f24-86bc-171f5f485cf8', '52a65fce-1c26-5bcf-980a-6edc78d79af8', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('4f8e9c44-d9de-5073-ab97-e764197d1f99', '5ef34c68-75f3-5b13-a472-a900d7c3e72b', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('c6637ffa-2d08-5106-8901-8b28d5b04f37', '370c9e26-8edd-5a59-bd60-fa3f25777625', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('27bd287b-dadf-547b-9e71-49e3942d16cd', '81449705-2ad0-5180-bb5f-1efa41c713f3', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('e21969a7-80f9-56bc-84bc-ec08cf90b1e9', 'd8e384ea-176b-5e08-a8a9-c422e0a6d132', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('8895c6a2-1e1a-5385-878c-1128ca567a51', 'd696bb98-861d-575c-9ac0-263ca97d3c86', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('50d3af59-df00-51ef-a804-d4da67280909', '55909e23-6eae-5237-b1a6-4fe831cfcad6', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('f1fc695c-f45b-5c3d-a276-1f912ea489c6', '4809bdf6-a358-5796-b001-ea405092c083', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('b8cb97e9-095d-57bb-92e4-68e799cf3d40', '204d0c14-9cb6-5344-b672-70584f41f352', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('e93a5445-b43d-54d3-9766-b0ec05cd080f', '6f5513ef-6d26-5c51-9194-e9b5bc982b1d', 'Standard', NULL, 399, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('61ec8bfd-75c1-554a-a894-f20a64b60c5b', 'd13b257e-d923-55dd-aad4-ddb51163cf1b', 'Small', NULL, 390, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0),
    ('1bce2507-2fbc-5774-995f-2bec894768f2', 'd13b257e-d923-55dd-aad4-ddb51163cf1b', 'Large', NULL, 750, 'AUD', false, true, true, 2, 'NOT_REVIEWED', 0),
    ('8bf357ec-1de7-52bb-8a2c-181e9ae23e77', 'ae51c989-3304-52fe-8748-e688984a1bbc', 'Standard', NULL, 750, 'AUD', true, true, true, 1, 'NOT_REVIEWED', 0);

INSERT INTO public.menu_collection_item
    (collection_id, menu_item_id, collection_category_id, display_order,
     price_override_minor, version)
VALUES
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '21f4de0c-44b5-5a84-92b7-52fa491b0a90', '7342f934-08e2-5993-8c2e-7143808eec86', 41, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'a3ccef91-4a88-5283-9a97-cfd46af73b98', '7342f934-08e2-5993-8c2e-7143808eec86', 42, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '08ff1fa5-54aa-5e16-b307-730d0217d2d5', '3571dea4-fb14-5a68-b2ec-2a3df8be4d74', 43, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '854558d7-399d-5694-acbe-53a52d5545b3', '3571dea4-fb14-5a68-b2ec-2a3df8be4d74', 44, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd26a641a-dfd4-5d09-acb3-1c2ddd5c3575', 'c112f549-a7b8-54aa-9aed-0e0051e9812f', 45, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '4b097ea6-8f08-5f94-92a8-20b599042aa9', 'c112f549-a7b8-54aa-9aed-0e0051e9812f', 46, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'aba29472-15e1-5f53-954b-3503c4b1083c', 'c112f549-a7b8-54aa-9aed-0e0051e9812f', 47, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '1fef33c0-34e3-5d86-b522-b26cb0a2b927', 'a5329912-01b1-5c08-bf01-c478ed5f48f5', 48, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '0ac4afbd-1d96-5411-87a3-e79e77c97463', 'a5329912-01b1-5c08-bf01-c478ed5f48f5', 49, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'b4e8b599-a6f2-5e0d-adb0-c9054e3226a0', 'a5329912-01b1-5c08-bf01-c478ed5f48f5', 50, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd6e7b5a5-abc2-50ba-ace1-da60f0ef4f29', 'a5329912-01b1-5c08-bf01-c478ed5f48f5', 51, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'f809c3c5-2d28-57ad-b728-598de99c84bc', 'a5329912-01b1-5c08-bf01-c478ed5f48f5', 52, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'b1584052-806a-5acc-8000-2b19f96770ae', '7920180c-5400-526b-a0de-e5cb71eb569f', 53, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3d53e079-a07b-5543-a681-5cade6b2679a', '7920180c-5400-526b-a0de-e5cb71eb569f', 54, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '07ed25fe-df00-5d1e-9d98-be7cca0f7bfd', '7920180c-5400-526b-a0de-e5cb71eb569f', 55, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '34c12338-b592-5c47-a808-345938a5f2a7', '7920180c-5400-526b-a0de-e5cb71eb569f', 56, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '25ee2f8a-7493-5108-9fc8-e884ef28dc9d', '7920180c-5400-526b-a0de-e5cb71eb569f', 57, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '8cbfb9a2-a854-5e3b-811b-4cd2e474826a', '7920180c-5400-526b-a0de-e5cb71eb569f', 58, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ad652f75-c1a3-5fde-abb6-39b67022f3ca', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 59, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '24941f25-fe8b-5408-a2ec-3e950ed3276c', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 60, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '610d8b1b-41f2-56b7-b70a-d40d0bf538ba', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 61, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '0742c5ed-65ef-5005-a618-fa7e12c2f1f6', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 62, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'c5639f69-d3a3-5884-9c20-2ae939a683a2', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 63, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '44334022-fa29-5f30-9c76-80986f8e42cc', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 64, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '1304484d-6ae8-5b61-8a9d-d3f7e0d50850', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 65, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '07259ce8-f781-5fd9-8c95-59bd0073bf87', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 66, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '3ad4f093-8bc7-5971-b50a-b5260c56d21b', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 67, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '6ba83645-542a-50de-ace7-5dc80e0ad21c', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 68, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '87fb719f-7a1a-5f9b-a018-2493d3146d07', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 69, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '45118c45-762a-5a0f-901c-ffa25646c074', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 70, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '52a65fce-1c26-5bcf-980a-6edc78d79af8', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 71, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '5ef34c68-75f3-5b13-a472-a900d7c3e72b', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 72, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '370c9e26-8edd-5a59-bd60-fa3f25777625', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 73, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '81449705-2ad0-5180-bb5f-1efa41c713f3', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 74, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd8e384ea-176b-5e08-a8a9-c422e0a6d132', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 75, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd696bb98-861d-575c-9ac0-263ca97d3c86', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 76, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '55909e23-6eae-5237-b1a6-4fe831cfcad6', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 77, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '4809bdf6-a358-5796-b001-ea405092c083', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 78, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '204d0c14-9cb6-5344-b672-70584f41f352', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 79, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', '6f5513ef-6d26-5c51-9194-e9b5bc982b1d', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 80, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'd13b257e-d923-55dd-aad4-ddb51163cf1b', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 81, NULL, 0),
    ('c14b4af7-d72a-5eae-86d0-9f2803a4ce73', 'ae51c989-3304-52fe-8748-e688984a1bbc', 'd9e1be1d-6029-5610-9fd6-0ec4195a91f8', 82, NULL, 0);

INSERT INTO public.menu_item_dietary_tag
    (menu_item_id, dietary_tag_id, notes, verified_at, version)
VALUES
    ('4b097ea6-8f08-5f94-92a8-20b599042aa9', '0a437e77-0441-5b51-901e-af2077d269ca', NULL, NULL, 0),
    ('4b097ea6-8f08-5f94-92a8-20b599042aa9', '19a270a5-a989-59c4-adc5-09d5264be463', NULL, NULL, 0);

-- Option groups from the printed page.
INSERT INTO public.menu_option_group
    (id, code, name, selection_type, is_active, version)
VALUES
    ('2f2c0f05-5c87-52c4-9279-dc82299b567f', 'soup-protein', 'Protein', 'SINGLE', true, 0),
    ('b01a1f44-ca95-5d07-a6d2-73fe99d26696', 'salad-protein', 'Protein', 'SINGLE', true, 0);

INSERT INTO public.menu_option
    (id, option_group_id, code, name, price_delta_minor, currency,
     display_order, is_active, version)
VALUES
    ('8f8f078c-950e-5b76-83a5-bc6c8e22c168', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'beef', 'Beef', 0, 'AUD', 1, true, 0),
    ('f8745307-423e-5372-8fa9-00f5d313ebb8', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'chicken', 'Chicken', 0, 'AUD', 2, true, 0),
    ('fcfc20a5-3609-56eb-9ffe-654778930c7d', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'veg-tofu', 'Veg & Tofu', 0, 'AUD', 3, true, 0),
    ('00ac1f4a-e5af-54b0-9122-6f68a1404a74', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'prawns', 'Prawns', 600, 'AUD', 4, true, 0),
    ('9df0263e-bbd1-5dd6-bd46-b32944efa7e0', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'seafood', 'Seafood', 800, 'AUD', 5, true, 0),
    ('8d3b686e-3dd3-53a6-ab77-fb1d8cb6f4bf', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'crispy-pork', 'Crispy Pork', 500, 'AUD', 6, true, 0),
    ('fe7ee82d-f76c-58e7-9289-01bfa081bf07', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 'fish', 'Fish', 600, 'AUD', 7, true, 0),
    ('8691e0ae-2efb-5ce4-b72d-d44781dacd92', 'b01a1f44-ca95-5d07-a6d2-73fe99d26696', 'chicken', 'Chicken', 0, 'AUD', 1, true, 0),
    ('d3ad2971-4ddd-5eec-af74-bb1bcadbaff8', 'b01a1f44-ca95-5d07-a6d2-73fe99d26696', 'beef', 'Beef', 0, 'AUD', 2, true, 0);

INSERT INTO public.menu_item_option_group
    (menu_item_id, option_group_id, min_selections, max_selections,
     display_order, version)
VALUES
    ('08ff1fa5-54aa-5e16-b307-730d0217d2d5', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 1, 1, 1, 0),
    ('854558d7-399d-5694-acbe-53a52d5545b3', '2f2c0f05-5c87-52c4-9279-dc82299b567f', 1, 1, 1, 0),
    ('d26a641a-dfd4-5d09-acb3-1c2ddd5c3575', 'b01a1f44-ca95-5d07-a6d2-73fe99d26696', 1, 1, 1, 0);

-- Verification guards.
DO $$
DECLARE
    new_item_count integer;
    main_menu_count integer;
    main_category_count integer;
    numbered_food_count integer;
BEGIN
    SELECT count(*) INTO new_item_count
    FROM public.menu_item
    WHERE id IN (
        '21f4de0c-44b5-5a84-92b7-52fa491b0a90',
        'a3ccef91-4a88-5283-9a97-cfd46af73b98',
        '08ff1fa5-54aa-5e16-b307-730d0217d2d5',
        '854558d7-399d-5694-acbe-53a52d5545b3',
        'd26a641a-dfd4-5d09-acb3-1c2ddd5c3575',
        '4b097ea6-8f08-5f94-92a8-20b599042aa9',
        'aba29472-15e1-5f53-954b-3503c4b1083c',
        '1fef33c0-34e3-5d86-b522-b26cb0a2b927',
        '0ac4afbd-1d96-5411-87a3-e79e77c97463',
        'b4e8b599-a6f2-5e0d-adb0-c9054e3226a0',
        'd6e7b5a5-abc2-50ba-ace1-da60f0ef4f29',
        'f809c3c5-2d28-57ad-b728-598de99c84bc',
        'b1584052-806a-5acc-8000-2b19f96770ae',
        '3d53e079-a07b-5543-a681-5cade6b2679a',
        '07ed25fe-df00-5d1e-9d98-be7cca0f7bfd',
        '34c12338-b592-5c47-a808-345938a5f2a7',
        '25ee2f8a-7493-5108-9fc8-e884ef28dc9d',
        '8cbfb9a2-a854-5e3b-811b-4cd2e474826a',
        'ad652f75-c1a3-5fde-abb6-39b67022f3ca',
        '24941f25-fe8b-5408-a2ec-3e950ed3276c',
        '610d8b1b-41f2-56b7-b70a-d40d0bf538ba',
        '0742c5ed-65ef-5005-a618-fa7e12c2f1f6',
        'c5639f69-d3a3-5884-9c20-2ae939a683a2',
        '44334022-fa29-5f30-9c76-80986f8e42cc',
        '1304484d-6ae8-5b61-8a9d-d3f7e0d50850',
        '07259ce8-f781-5fd9-8c95-59bd0073bf87',
        '3ad4f093-8bc7-5971-b50a-b5260c56d21b',
        '6ba83645-542a-50de-ace7-5dc80e0ad21c',
        '87fb719f-7a1a-5f9b-a018-2493d3146d07',
        '45118c45-762a-5a0f-901c-ffa25646c074',
        '52a65fce-1c26-5bcf-980a-6edc78d79af8',
        '5ef34c68-75f3-5b13-a472-a900d7c3e72b',
        '370c9e26-8edd-5a59-bd60-fa3f25777625',
        '81449705-2ad0-5180-bb5f-1efa41c713f3',
        'd8e384ea-176b-5e08-a8a9-c422e0a6d132',
        'd696bb98-861d-575c-9ac0-263ca97d3c86',
        '55909e23-6eae-5237-b1a6-4fe831cfcad6',
        '4809bdf6-a358-5796-b001-ea405092c083',
        '204d0c14-9cb6-5344-b672-70584f41f352',
        '6f5513ef-6d26-5c51-9194-e9b5bc982b1d',
        'd13b257e-d923-55dd-aad4-ddb51163cf1b',
        'ae51c989-3304-52fe-8748-e688984a1bbc'
    );

    SELECT count(*) INTO main_menu_count
    FROM public.menu_collection_item
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    SELECT count(*) INTO main_category_count
    FROM public.menu_collection_category
    WHERE collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73';

    SELECT count(*) INTO numbered_food_count
    FROM public.menu_collection_item mci
    JOIN public.menu_item mi ON mi.id = mci.menu_item_id
    WHERE mci.collection_id = 'c14b4af7-d72a-5eae-86d0-9f2803a4ce73'
      AND (
          mi.name ~ '^[0-9]+[.] '
          OR mi.name LIKE '14A. %'
      );

    IF new_item_count <> 42 THEN
        RAISE EXCEPTION 'Expected 42 new V20 items; found %', new_item_count;
    END IF;
    IF main_menu_count <> 82 THEN
        RAISE EXCEPTION 'Expected 82 Main Menu memberships after V20; found %', main_menu_count;
    END IF;
    IF main_category_count <> 13 THEN
        RAISE EXCEPTION 'Expected 13 Main Menu categories after V20; found %', main_category_count;
    END IF;
    IF numbered_food_count <> 58 THEN
        RAISE EXCEPTION 'Expected 58 numbered Main Menu food item names; found %',
            numbered_food_count;
    END IF;
END
$$;
