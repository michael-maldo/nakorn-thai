-- Transcribed from the two supplied Chef's Special Recommendations photographs.
-- Prices are AUD cents; spelling/punctuation normalized, no food-safety claims inferred.
-- Existing signature dish identity, content and images are preserved.
-- Deliberately fail on conflicting item/collection slugs instead of overwriting staff edits.

INSERT INTO menu_category (id,name,slug,display_order) VALUES ('f379919c-35c6-5ee2-996f-5fcdf34b5a29','Entrées','entrees',3) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('b3e37868-17e7-5467-add1-056da0b86855','Rice','rice',4) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('c0902c36-e4d6-5b72-b011-c514962c3a85','Salads','salads',5) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('bc186f3b-82fc-5ccd-9c9a-ac7af98ab615','Seafood','seafood',6) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_collection (id,name,slug,status,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','Chef''s Special Recommendations','chefs-special-recommendations','PUBLISHED',2);
INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '2c204cd3-1c7a-591e-9bf2-b36bfe6c626d',id,'Crispy Fish Balls','crispy-fish-balls','Golden crispy fish balls served with Thai sweet chilli sauce.','PUBLISHED',1
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('20440ff6-df16-52a3-bb6f-a7fa9c8d1a55','2c204cd3-1c7a-591e-9bf2-b36bfe6c626d','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','2c204cd3-1c7a-591e-9bf2-b36bfe6c626d',1);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '6d33e698-dde9-51b4-bec5-40cbc1412a1d',id,'Golden Crispy Chicken Maryland with Skin','golden-crispy-chicken-maryland-with-skin','Served with Thai sweet chilli sauce.','PUBLISHED',2
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('351cfcae-04d5-5545-8fa8-83f9b7dad5a8','6d33e698-dde9-51b4-bec5-40cbc1412a1d','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','6d33e698-dde9-51b4-bec5-40cbc1412a1d',2);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'c9e8b092-0c2e-5f09-8d7f-7ac518c6af04',id,'Golden Bags','golden-bags','Rice pastry filled with carrots, chicken mince, snow peas and green beans, served with sweet chilli sauce.','PUBLISHED',3
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('54ad58f5-0b9d-578a-b129-cec16b69f1c4','c9e8b092-0c2e-5f09-8d7f-7ac518c6af04','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','c9e8b092-0c2e-5f09-8d7f-7ac518c6af04',3);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '26762f26-afd6-5f99-ac3c-4f707ab1a76d',id,'Grilled Pork Skewers with Sticky Rice','grilled-pork-skewers-with-sticky-rice','Served with Thai chilli tamarind sauce.','PUBLISHED',4
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('815b57ad-a73a-546e-bb05-a414be635597','26762f26-afd6-5f99-ac3c-4f707ab1a76d','Standard',1590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','26762f26-afd6-5f99-ac3c-4f707ab1a76d',4);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '0bc30656-09a2-5201-831e-c1c9799e2133',id,'Signature Wings','signature-wings','Crispy fried chicken wings served with sweet chilli sauce.','PUBLISHED',5
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('7f7b8666-703f-5eab-982b-193adc1fdd17','0bc30656-09a2-5201-831e-c1c9799e2133','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','0bc30656-09a2-5201-831e-c1c9799e2133',5);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'a247467e-ee3e-56f0-bc75-30f9fe67cf67',id,'Crispy Egg Pops','crispy-egg-pops','Crunchy wonton-wrapped quail eggs on skewers, served with sweet chilli sauce.','PUBLISHED',6
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dd190f8b-458e-50df-9062-a76652b7ac93','a247467e-ee3e-56f0-bc75-30f9fe67cf67','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','a247467e-ee3e-56f0-bc75-30f9fe67cf67',6);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '87ddf0a1-711a-5b09-a5e8-eaef163ab4d6',id,'Soft Shell Crab','soft-shell-crab','Deep-fried soft-shell crab, served with a tangy Thai-style seafood dipping sauce.','PUBLISHED',7
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e31e5f19-603a-5125-92a6-1e9e9dec4b26','87ddf0a1-711a-5b09-a5e8-eaef163ab4d6','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','87ddf0a1-711a-5b09-a5e8-eaef163ab4d6',7);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'ad768263-8901-57a6-91c0-5eeb47151904',id,'Prawns Spring Rolls','prawns-spring-rolls','Spring rolls filled with marinated black tiger prawn, served with sweet chilli sauce.','PUBLISHED',8
FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('37053bb0-5c40-5e35-a2f6-8298599a3d24','ad768263-8901-57a6-91c0-5eeb47151904','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','ad768263-8901-57a6-91c0-5eeb47151904',8);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '24cbeb0d-3a2a-5203-a788-e802b030a11c',id,'Crab Fried Rice','crab-fried-rice','Wok-fried jasmine rice with fresh crab meat, egg and spring onion, seasoned.','PUBLISHED',9
FROM menu_category WHERE slug='rice';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('638da1e3-91e9-5eaf-851d-16df1a20c917','24cbeb0d-3a2a-5203-a788-e802b030a11c','Standard',2790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','24cbeb0d-3a2a-5203-a788-e802b030a11c',9);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '60941112-cf43-5122-9818-aca3dd19a2fd',id,'Soft Shell Crab Curry Stir-Fry','soft-shell-crab-curry-stir-fry','Crispy soft-shell crab with eggs, onion, curry powder and milk in rich Thai-style curry sauce.','PUBLISHED',10
FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0a0f5c28-e528-5043-8c0d-5e4719b6c650','60941112-cf43-5122-9818-aca3dd19a2fd','Standard',2790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','60941112-cf43-5122-9818-aca3dd19a2fd',10);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '3157eddf-1f40-52fa-bfce-243128c0f6fe',id,'Red Duck Curry','red-duck-curry','Tender roasted duck in rich Thai red curry with coconut milk, tomato, pineapple and basil.','PUBLISHED',11
FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('da013566-f867-50aa-abd4-e1bd174ae47c','3157eddf-1f40-52fa-bfce-243128c0f6fe','Standard',2890,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','3157eddf-1f40-52fa-bfce-243128c0f6fe',11);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '55b8d0b0-a133-5d9c-ab1f-5543357bc856',id,'Roast Duck with Chinese Broccoli','roast-duck-with-chinese-broccoli','Roast duck served with wok-tossed Chinese broccoli and light soy garlic sauce.','PUBLISHED',12
FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b8c29d68-ecb5-5ab1-a466-9be7c7756b2e','55b8d0b0-a133-5d9c-ab1f-5543357bc856','Standard',2890,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','55b8d0b0-a133-5d9c-ab1f-5543357bc856',12);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '49f68b33-c42b-5162-a2d4-019b7dd5c374',id,'Lamb Shank with Curry','lamb-shank-with-curry','Green curry, Red curry, Yellow curry, and Massaman curry.','PUBLISHED',13
FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e3d30315-8fc1-5042-ad52-7741f2130839','49f68b33-c42b-5162-a2d4-019b7dd5c374','Green Curry',3190,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f54593db-ebba-59b1-b589-7ec0955b610f','49f68b33-c42b-5162-a2d4-019b7dd5c374','Red Curry',3190,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2109d670-3f2c-55d8-9088-b42795b91a5d','49f68b33-c42b-5162-a2d4-019b7dd5c374','Yellow Curry',3190,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9bcbf6b5-302b-5132-a7a7-92b86310d244','49f68b33-c42b-5162-a2d4-019b7dd5c374','Massaman Curry',3190,'AUD',false,4);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','49f68b33-c42b-5162-a2d4-019b7dd5c374',13);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '65f3b3cd-9539-5ff8-9c64-eed096c6f678',id,'Salmon Green Curry','salmon-green-curry','Salmon cooked in Thai green curry with coconut milk and vegetables.','PUBLISHED',14
FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('60e938b8-66ee-5244-9b8f-c089c4f3f6ec','65f3b3cd-9539-5ff8-9c64-eed096c6f678','Standard',2890,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','65f3b3cd-9539-5ff8-9c64-eed096c6f678',14);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'b88e2d09-33dd-56f8-b885-0ecae6319328',id,'Asian Green Stir-Fry','asian-green-stir-fry','Fresh seasonal Asian greens wok-tossed with garlic and light soy sauce.','PUBLISHED',15
FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('375ca44d-09db-5d4c-9d00-b083af2dc49c','b88e2d09-33dd-56f8-b885-0ecae6319328','Standard',2390,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','b88e2d09-33dd-56f8-b885-0ecae6319328',15);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT '2e052582-f3e4-50f8-8c30-784e2a47f948',id,'Thai Crispy King Prawns','thai-crispy-king-prawns','Served with sweet chilli tamarind sauce.','PUBLISHED',16
FROM menu_category WHERE slug='seafood';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2d7d2075-3e75-5118-8f2a-a2750cc3eb2f','2e052582-f3e4-50f8-8c30-784e2a47f948','Standard',2990,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','2e052582-f3e4-50f8-8c30-784e2a47f948',16);

-- Reuse the original Crispy Pork & Broccoli; keep staff-managed name/description.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM menu_item WHERE id='20000000-0000-0000-0000-000000000004')
 OR EXISTS (SELECT 1 FROM menu_item_variation WHERE menu_item_id='20000000-0000-0000-0000-000000000004') THEN
  RAISE EXCEPTION 'Chef menu import: existing Crispy Pork & Broccoli is missing or already has variations; reconcile before importing';
 END IF;
END $$;
UPDATE menu_item SET version=version+1 WHERE id='20000000-0000-0000-0000-000000000004';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('928b8303-16f2-5861-883d-2a1dac5c0754','20000000-0000-0000-0000-000000000004','Standard',2490,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','20000000-0000-0000-0000-000000000004',17);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'ffaf89c1-d4d0-558f-bbb5-8b307570f62f',id,'Thai Basil Pork Mince','thai-basil-pork-mince','Stir-fried minced pork with Thai basil, chilli, garlic and soy sauce.','PUBLISHED',18
FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9056b9dc-84ec-59e9-b540-7b00b967ada4','ffaf89c1-d4d0-558f-bbb5-8b307570f62f','Standard',2390,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','ffaf89c1-d4d0-558f-bbb5-8b307570f62f',18);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'fd4fdbed-051d-542a-8790-b062ae0ea530',id,'Salmon Salad','salmon-salad','Salmon slices with fresh lime chilli, tomato, onion, spring onion and dry shallots.','PUBLISHED',19
FROM menu_category WHERE slug='salads';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9e411162-fc34-5c51-89b3-dbe30f160420','fd4fdbed-051d-542a-8790-b062ae0ea530','Standard',2590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','fd4fdbed-051d-542a-8790-b062ae0ea530',19);

INSERT INTO menu_item (id,category_id,name,slug,description,status,display_order)
SELECT 'bdb727bd-5adc-517d-b91a-87a071d1aca0',id,'Crispy Pork Salad','crispy-pork-salad','Crispy pork slices with fresh lime chilli, tomato, onion, spring onion and dry shallot.','PUBLISHED',20
FROM menu_category WHERE slug='salads';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('74aa2af3-7783-5b19-b15b-6c74972da96c','bdb727bd-5adc-517d-b91a-87a071d1aca0','Standard',2590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('49a34f40-893c-5d8c-9311-34c8ce6f02ae','bdb727bd-5adc-517d-b91a-87a071d1aca0',20);

