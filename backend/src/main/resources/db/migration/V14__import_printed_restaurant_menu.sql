-- Printed regular menu, lunch specials and drinks supplied by the restaurant.
-- AUD cents. Existing signature/chef dishes and staff edits are preserved.
-- Lunch is browse-only until recurring service-hour enforcement is implemented.
-- Sizzling Hot Plate has no price in this import: image glare; awaiting confirmation.
-- Printed dietary labels are retained as source text, not verified dietary/allergen declarations.

INSERT INTO menu_category (id,name,slug,display_order) VALUES ('fc584353-2309-5c59-8273-62356e2ee7e4','Entrées','entrees',10) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('a1e68929-cebb-52fd-8f8c-3aef21045b92','Fish','fish',11) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('9d24b012-3ab4-515b-963a-9dfa3d346c65','Curries','curries',12) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('e732e24d-962e-5b16-8298-536a9a7492d9','Stir-fries','stir-fries',13) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('b5a19756-8160-5995-8843-d9c2086ea945','BBQ & Grilled','bbq-grilled',14) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('63aef3ff-89f5-5e6d-90b2-b4641641116f','Noodle Stir-Fry','noodles',15) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('3db235b1-c731-56c3-9ab4-22b8573af5cb','Rice','rice',16) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('1e3ecc1e-ea17-55cf-be78-0b895288cbc0','Noodle Soup','noodle-soup',17) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('5de1f03e-cbcc-581e-9452-f4bd7b656157','Soups','soups',18) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('7022f2cd-04ea-533e-b456-b37d655349f4','Salads','salads',19) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('ba770897-043a-5f30-a893-84ef0ba7ddb4','Sides','sides',20) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('e2aa275a-a0a2-5c19-a019-941ae9953e91','Desserts','desserts',21) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('775f7a6a-e392-5101-8178-7f4f3da50338','Lunch Specials','lunch',22) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_category (id,name,slug,display_order) VALUES ('f11627e6-c3e7-51c6-9319-137effa8b651','Drinks','drinks',23) ON CONFLICT (slug) DO NOTHING;
INSERT INTO menu_collection (id,name,slug,description,status,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','Restaurant Menu','regular-menu','Entrées, main dishes, sides and desserts.','PUBLISHED',3);
INSERT INTO menu_collection (id,name,slug,description,status,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','Lunch Specials','lunch-specials','Lunch specials till 2:30 PM. Online ordering unavailable pending service-hour configuration.','PUBLISHED',4);
INSERT INTO menu_collection (id,name,slug,description,status,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','Drinks','drinks','Tea, coffee, juices, soft drinks and water.','PUBLISHED',5);

-- Printed entry 1: Prawn Crackers with Chilli Jam
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'dce96273-baca-5859-a967-39960a338474',id,'Prawn Crackers with Chilli Jam','prawn-crackers-with-chilli-jam','Served with homemade chilli jam.','PUBLISHED',true,1 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('df3e65d5-48c9-5092-9d01-869bd62b397c','dce96273-baca-5859-a967-39960a338474','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','dce96273-baca-5859-a967-39960a338474',1);

-- Printed entry 2: Roti (2 pieces)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'aac28019-7276-5499-92fc-4cfeb06bf080',id,'Roti (2 pieces)','roti-2-pieces','Crispy roti bread lightly buttered, served with homemade peanut sauce.','PUBLISHED',true,2 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6b593174-0229-539b-9b7d-d9ccffb1e4b1','aac28019-7276-5499-92fc-4cfeb06bf080','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','aac28019-7276-5499-92fc-4cfeb06bf080',2);

-- Printed entry 3: Chicken Spring Rolls (4 pieces)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '6297fbad-8af4-5a22-9f3c-9189938290a3',id,'Chicken Spring Rolls (4 pieces)','chicken-spring-rolls-4-pieces','Served with sweet chilli sauce.','PUBLISHED',true,3 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('020b6f2b-3de0-583b-a72d-4822117d89c7','6297fbad-8af4-5a22-9f3c-9189938290a3','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','6297fbad-8af4-5a22-9f3c-9189938290a3',3);

-- Printed entry 4: Vegetable Spring Rolls (4 pieces)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '2a9ea6ae-ee9e-5db5-9de7-b858aed028ae',id,'Vegetable Spring Rolls (4 pieces)','vegetable-spring-rolls-4-pieces','Served with sweet chilli sauce.','PUBLISHED',true,4 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5e1825e1-e289-54c2-a712-bada69987685','2a9ea6ae-ee9e-5db5-9de7-b858aed028ae','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','2a9ea6ae-ee9e-5db5-9de7-b858aed028ae',4);

-- Printed entry 5: Vegetable Curry Puffs (4 pieces)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'abd0c67f-6892-56d9-8ff2-08b02fb4b16c',id,'Vegetable Curry Puffs (4 pieces)','vegetable-curry-puffs-4-pieces','Puff pastry filled with potatoes, carrots and peas.','PUBLISHED',true,5 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4ecac567-8519-5068-8ac7-7a7b9a9b0be0','abd0c67f-6892-56d9-8ff2-08b02fb4b16c','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','abd0c67f-6892-56d9-8ff2-08b02fb4b16c',5);

-- Printed entry 6: Fish Cakes (4 pieces)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'b782f75a-3816-5f43-b628-3cd2716729fb',id,'Fish Cakes (4 pieces)','fish-cakes-4-pieces','Fish fillet mixed with Thai herbs, served with sweet chilli sauce.','PUBLISHED',true,6 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('fa3a8d78-6f86-5e2b-9860-ab1246fa405a','b782f75a-3816-5f43-b628-3cd2716729fb','Standard',1190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','b782f75a-3816-5f43-b628-3cd2716729fb',6);

-- Printed entry 7: Salt & Pepper Calamari
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '935c9e3d-d5d4-516b-b99b-ddd68197c2e7',id,'Salt & Pepper Calamari','salt-pepper-calamari','Served with a green salad and side of tartar sauce.','PUBLISHED',true,7 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1dadbab2-6fbc-5e8f-a4eb-ed179abbf60f','935c9e3d-d5d4-516b-b99b-ddd68197c2e7','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','935c9e3d-d5d4-516b-b99b-ddd68197c2e7',7);

-- Printed entry 8: Calamari Rings
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '53e6b462-c292-5c0e-bae0-db974130866a',id,'Calamari Rings','calamari-rings','Crumbed calamari rings served with sweet chilli sauce.','PUBLISHED',true,8 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('94ead4bd-f9dd-5e29-9d0a-45db3df42adf','53e6b462-c292-5c0e-bae0-db974130866a','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','53e6b462-c292-5c0e-bae0-db974130866a',8);

-- Printed entry 9: Tempura Prawns
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '31398d3e-ce8f-5d57-a19b-813367ef2f56',id,'Tempura Prawns','tempura-prawns','Served with sweet chilli sauce.','PUBLISHED',true,9 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('35d3b6d0-2a56-5157-961d-a31d45a8ab9b','31398d3e-ce8f-5d57-a19b-813367ef2f56','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','31398d3e-ce8f-5d57-a19b-813367ef2f56',9);

-- Printed entry 10: Satay Chicken (4 skewers)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '4f641221-5c8e-57ef-8607-7ebc3775e8a7',id,'Satay Chicken (4 skewers)','satay-chicken-4-skewers','Chicken tenderloin marinated in Thai spices, served with homemade peanut sauce. Printed menu label: GF.','PUBLISHED',true,10 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('27c28b15-fe61-5005-9a92-c126d43f6a0a','4f641221-5c8e-57ef-8607-7ebc3775e8a7','Standard',1390,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','4f641221-5c8e-57ef-8607-7ebc3775e8a7',10);

-- Printed entry 11: Vegetable Tempura
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'c10d862e-ea7d-55f7-a186-e6f7ca2b09bb',id,'Vegetable Tempura','vegetable-tempura','Served with sweet chilli sauce. Printed menu label: VG.','PUBLISHED',true,11 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('07112931-ab7b-5f41-9a5e-e31a485ecfaa','c10d862e-ea7d-55f7-a186-e6f7ca2b09bb','Standard',1790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','c10d862e-ea7d-55f7-a186-e6f7ca2b09bb',11);

-- Printed entry 12: Chives Pancake
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '6f25972f-38bd-5799-9ef1-9d37d0cbc34e',id,'Chives Pancake','chives-pancake','Tapioca, rice flour, chives and sweet soy sauce. Printed menu label: VG.','PUBLISHED',true,12 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('023ca1b9-1291-5377-a86d-0c84cc9bd00d','6f25972f-38bd-5799-9ef1-9d37d0cbc34e','Standard',1590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','6f25972f-38bd-5799-9ef1-9d37d0cbc34e',12);

-- Printed entry 13: Mix Plate
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '03b36493-2f66-52d0-bf67-95966113c8d1',id,'Mix Plate','mix-plate','1 vegetable and 1 chicken spring roll, 1 satay chicken, 1 curry puff and 1 fish cake.','PUBLISHED',true,13 FROM menu_category WHERE slug='entrees';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8419b148-866d-576c-ba29-5b0540c8adab','03b36493-2f66-52d0-bf67-95966113c8d1','Standard',1690,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','03b36493-2f66-52d0-bf67-95966113c8d1',13);

-- Printed entry 14: Whole Barramundi Fish
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '67761c97-7e47-521b-af77-9b302694afc5',id,'Whole Barramundi Fish','whole-barramundi-fish','Deep-fried only. Choice of sauce.','PUBLISHED',true,14 FROM menu_category WHERE slug='fish';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ccc2f13a-768e-5ab7-adc5-d03a0c693863','67761c97-7e47-521b-af77-9b302694afc5','Chilli & Tamarind Sauce',4590,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('de04c751-9e8f-57e6-b936-44350f705bd9','67761c97-7e47-521b-af77-9b302694afc5','Fresh Ginger Sauce',4590,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('410b0ae1-978e-5ef3-9657-971af61cf96d','67761c97-7e47-521b-af77-9b302694afc5','Sweet & Sour Sauce',4590,'AUD',false,3);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','67761c97-7e47-521b-af77-9b302694afc5',14);

-- Printed entry 14A: Fish Fillet
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '609988b4-fea8-5c59-9961-854ce35a445a',id,'Fish Fillet','fish-fillet','Deep-fried only. Choice of sauce.','PUBLISHED',true,15 FROM menu_category WHERE slug='fish';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ce8724f7-79d4-5b52-84c1-9152c1c4273f','609988b4-fea8-5c59-9961-854ce35a445a','Chilli & Tamarind Sauce',2890,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('60ef2e22-4692-5d8d-9477-e15356a3a4e3','609988b4-fea8-5c59-9961-854ce35a445a','Fresh Ginger Sauce',2890,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('45718ad2-9cd1-5caf-97cc-d660b22383ab','609988b4-fea8-5c59-9961-854ce35a445a','Sweet & Sour Sauce',2890,'AUD',false,3);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','609988b4-fea8-5c59-9961-854ce35a445a',15);

-- Printed entry 15: Green Curry (À la carte)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'c4e4e18b-8365-5a45-9ad4-e747fe79e671',id,'Green Curry (À la carte)','green-curry-a-la-carte','With pumpkin, green beans, zucchini, capsicum, bamboo and basil. Printed menu label: GF.','PUBLISHED',true,16 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('52526939-b1de-5632-ad37-9599ebb8022a','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4c238ab6-8bd3-54d1-8698-9a1515a09aff','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c784f56b-1da1-5fa8-889d-9e618e18e5d5','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0a64a279-a331-551d-a8ef-03c7d9ddf33a','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d2ebe464-709a-5b4d-aef6-2e9aea77a8f7','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('22bb0c23-f470-5f64-b7e6-0e78a477cad3','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('879d4258-cc4c-5b5f-92bf-4ac80e2a1e0d','c4e4e18b-8365-5a45-9ad4-e747fe79e671','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','c4e4e18b-8365-5a45-9ad4-e747fe79e671',16);

-- Printed entry 16: Red Curry
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '31c876af-827b-55bc-9f34-6937696e2702',id,'Red Curry','red-curry','With pumpkin, green beans, zucchini, capsicum, bamboo and basil. Printed menu label: GF.','PUBLISHED',true,17 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b2ce1152-34d9-512b-b550-0406ac665432','31c876af-827b-55bc-9f34-6937696e2702','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5eafd117-12b4-589d-b351-fe9a5280fc92','31c876af-827b-55bc-9f34-6937696e2702','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('79238ce2-91bc-53ce-8ebe-243ba24c090a','31c876af-827b-55bc-9f34-6937696e2702','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e7347da4-8d3b-5b4b-80c4-63b3dd5e9951','31c876af-827b-55bc-9f34-6937696e2702','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('93285a15-00f6-547e-9aaf-516705f2c1be','31c876af-827b-55bc-9f34-6937696e2702','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b8e4ea59-7b9c-54ad-9442-64fd6389bef4','31c876af-827b-55bc-9f34-6937696e2702','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('fc1cd589-217a-585a-8b2f-d0b91dd426c2','31c876af-827b-55bc-9f34-6937696e2702','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','31c876af-827b-55bc-9f34-6937696e2702',17);

-- Printed entry 17: Yellow Curry (À la carte)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '6d058225-95dc-5827-9ecd-e3cda4b2072d',id,'Yellow Curry (À la carte)','yellow-curry-a-la-carte','With potatoes, carrot and onion. Printed menu label: GF.','PUBLISHED',true,18 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('60844b5e-0d9d-596f-948d-011a8c9155d5','6d058225-95dc-5827-9ecd-e3cda4b2072d','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ca662417-2064-5a2a-818f-5efb63137be4','6d058225-95dc-5827-9ecd-e3cda4b2072d','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c0d64956-5f1c-56cd-ac74-423a6f4932b9','6d058225-95dc-5827-9ecd-e3cda4b2072d','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3a47c384-4631-5d7c-a4dd-2021641bdf4e','6d058225-95dc-5827-9ecd-e3cda4b2072d','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8bae9438-90da-5f5a-a3f2-e28cb698f49c','6d058225-95dc-5827-9ecd-e3cda4b2072d','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('42451d22-a190-571b-b64e-94e0e28533a0','6d058225-95dc-5827-9ecd-e3cda4b2072d','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('faa48484-f2b6-5dbd-bc54-a8e07c4697f1','6d058225-95dc-5827-9ecd-e3cda4b2072d','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','6d058225-95dc-5827-9ecd-e3cda4b2072d',18);

-- Printed entry 18: Panang Curry
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'd9578512-be92-5b19-be52-00be40daee10',id,'Panang Curry','panang-curry','With pumpkin, carrot, bamboo, green beans, red capsicum and kaffir lime leaves. Printed menu label: GF.','PUBLISHED',true,19 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1698ea78-70a0-5674-a350-1e5c0eace991','d9578512-be92-5b19-be52-00be40daee10','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('abba1b93-f245-51ef-a14f-74c36073fab3','d9578512-be92-5b19-be52-00be40daee10','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('26d15407-a722-5187-b163-b59f7f016e8d','d9578512-be92-5b19-be52-00be40daee10','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('61344765-2667-5eb2-b09f-ee763b97496e','d9578512-be92-5b19-be52-00be40daee10','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a36923ea-bc50-50f7-b511-45a02eecc75c','d9578512-be92-5b19-be52-00be40daee10','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2c140a6c-03d1-564e-841a-05b5bd884f93','d9578512-be92-5b19-be52-00be40daee10','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d27811e5-cb79-514c-a4f0-fb9444fe0519','d9578512-be92-5b19-be52-00be40daee10','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','d9578512-be92-5b19-be52-00be40daee10',19);

-- Printed entry 19: Massaman Beef Curry
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '941ee78b-0a26-55e7-a436-9c5473f3869e',id,'Massaman Beef Curry','massaman-beef-curry','Slow-cooked beef with potato and carrot, topped with peanuts. No options. Printed menu label: GF.','PUBLISHED',true,20 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('36eeb2ce-4d67-523d-b68a-1e6fd59d22c5','941ee78b-0a26-55e7-a436-9c5473f3869e','Standard',2590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','941ee78b-0a26-55e7-a436-9c5473f3869e',20);

-- Printed entry 20: Jungle Curry
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'a54ede46-c734-5a66-9d22-4e39000d8aa3',id,'Jungle Curry','jungle-curry','Red curry paste, young green pepper, mixed vegetables, mushroom and mixed herbs. Printed menu label: GF.','PUBLISHED',true,21 FROM menu_category WHERE slug='curries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1eca21a6-865c-5e28-8128-08e276664537','a54ede46-c734-5a66-9d22-4e39000d8aa3','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9f46e9ce-3008-5c05-b45f-5ee26401ab8b','a54ede46-c734-5a66-9d22-4e39000d8aa3','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3e1ce018-66d1-5558-94da-b2bb85366dfd','a54ede46-c734-5a66-9d22-4e39000d8aa3','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f297d267-4fb5-5e61-b803-e0f743b38cf6','a54ede46-c734-5a66-9d22-4e39000d8aa3','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('92ce5eac-0929-5597-8a13-151d7222d0e4','a54ede46-c734-5a66-9d22-4e39000d8aa3','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('809fae20-78e1-5b90-972b-0d66aab385ec','a54ede46-c734-5a66-9d22-4e39000d8aa3','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6abef067-d792-5003-b3a7-97f7c216f0dc','a54ede46-c734-5a66-9d22-4e39000d8aa3','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','a54ede46-c734-5a66-9d22-4e39000d8aa3',21);

-- Printed entry 21: Cashew Nuts with Chilli Jam
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '58f7c64e-8b9a-5d04-a090-bba3d7e00a95',id,'Cashew Nuts with Chilli Jam','cashew-nuts-with-chilli-jam','Onion, capsicum, zucchini, broccoli, cashew nuts and spring onion.','PUBLISHED',true,22 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('964ce96e-98a2-5400-8fee-eb3d8bed69f1','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e5c68558-966e-5b3d-93b7-19c51ac15130','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6ae4acd5-7e83-5d19-abc6-a79cc6002155','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1397a293-9df2-5c0b-b6ad-c1df68352f22','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d19e03e4-8e46-5742-9515-2d91caed10ff','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('648c9559-5f63-5acc-becb-a9109efb3e2e','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('077e03d0-fee9-5cae-8c3c-187f7e04e568','58f7c64e-8b9a-5d04-a090-bba3d7e00a95','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','58f7c64e-8b9a-5d04-a090-bba3d7e00a95',22);

-- Printed entry 22: Sweet and Sour (Pad Priew Wang)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'c9d455a1-6c3b-5979-a25a-713d6e9f24d0',id,'Sweet and Sour (Pad Priew Wang)','sweet-and-sour-pad-priew-wang','Tomato, cucumber, onion, capsicum, pineapple and spring onion.','PUBLISHED',true,23 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('517e6099-7815-5e1b-8271-da427a0057af','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4b7fb36f-73a3-56ef-b681-bcc0585329b2','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8774e344-83b4-5bfb-be92-10e7c129b346','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9659ed72-92da-5e5d-b1cd-4bd8502ea588','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ff800641-e734-55ee-9341-6eb99e5e9fa9','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('45f6e170-60e4-5634-8f70-a6bac8d3a897','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4122de84-c800-599b-881e-0f16b8f2a5e1','c9d455a1-6c3b-5979-a25a-713d6e9f24d0','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','c9d455a1-6c3b-5979-a25a-713d6e9f24d0',23);

-- Printed entry 23: Fresh Ginger (Pad Khing)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '5940229f-dba8-5d6a-b4f2-4cc2f016194f',id,'Fresh Ginger (Pad Khing)','fresh-ginger-pad-khing','Fresh ginger, onion, fresh chilli, black mushroom, mushroom and spring onion.','PUBLISHED',true,24 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c1f12bcb-12f7-5453-9150-c920eac14aa7','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0f33736b-4b9e-52ba-b37b-8651b412456f','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('92f64707-e17e-5280-b5b7-bfcd53536066','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f64c5c07-a22d-52d8-ab61-86d48de09613','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4abfa3b8-3d3f-5986-ad1f-3f54a737e81d','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('32d8e477-94aa-58f3-9f38-32c8f9ef182f','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('cfe6028d-00c0-5aca-b0ba-4fcf8f81b64d','5940229f-dba8-5d6a-b4f2-4cc2f016194f','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','5940229f-dba8-5d6a-b4f2-4cc2f016194f',24);

-- Printed entry 24: Pad Kra Pao
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'b3d8e32c-e04a-5a06-9229-796c4df84cf1',id,'Pad Kra Pao','pad-kra-pao','Garlic, chilli, onion, capsicum, zucchini, carrot, broccoli, green beans, bamboo shoot and basil.','PUBLISHED',true,25 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2f88d563-4226-5127-850b-4ee1718f14b6','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f3299df5-7647-591e-b7c9-6188c3fc616d','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('7b8036eb-1eba-59de-9508-a6b38b5f4f10','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8b796743-2aef-5853-9b4e-278f488f7cd3','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1451c15f-8bbb-57a3-893d-fcacd32ccc53','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d140e029-7bbb-512f-9fa2-1216327a9706','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d1997494-bccc-5f67-a2df-8a4f8441180b','b3d8e32c-e04a-5a06-9229-796c4df84cf1','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','b3d8e32c-e04a-5a06-9229-796c4df84cf1',25);

-- Printed entry 25: Garlic and Pepper
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '2d6c871f-3fd2-544c-9a66-df34e4986f5b',id,'Garlic and Pepper','garlic-and-pepper','Broccoli, carrot, zucchini, snow peas, garlic, onion and black pepper.','PUBLISHED',true,26 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e3c3cc89-467f-5ea6-9a9a-c4c1dce4bdfe','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('70292772-70a0-5dd5-93a1-a88833c1213f','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('eb2aa974-0482-5d64-9832-9af011b1ac27','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d1367bbe-8efc-580e-8b05-080ae0a75f31','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('28999368-df60-562d-bb2a-77447e11b720','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a87886cf-d490-5ebb-84f0-0db017067745','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9a14cb42-69a5-532c-b51a-30bb7258dd10','2d6c871f-3fd2-544c-9a66-df34e4986f5b','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','2d6c871f-3fd2-544c-9a66-df34e4986f5b',26);

-- Printed entry 26: Satay Stir-Fried
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'c483afb6-54d0-5db5-8ae5-40ebd28f0509',id,'Satay Stir-Fried','satay-stir-fried','Broccoli, carrot, zucchini, mushroom and pak choi with homemade peanut sauce.','PUBLISHED',true,27 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('255b1873-508a-5578-95cf-b6a6552e7e38','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2b7a18dc-23a0-524f-a350-1a8b29cd0246','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c7b5727a-339b-503c-a207-ca00dd6d38d3','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('61396856-2588-5636-a556-ef1bc4f55f0c','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('607a9000-7b23-5c3e-91d0-9013e49c0346','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9aa2ecac-f339-5136-8926-816e258ce8cf','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8ce07d75-788e-5404-af83-75eb66c781e1','c483afb6-54d0-5db5-8ae5-40ebd28f0509','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','c483afb6-54d0-5db5-8ae5-40ebd28f0509',27);

-- Printed entry 28: Stir-Fried Basil with Deep-Fried Eggplant
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '7710d8ce-7ae9-5941-845a-ad9b9451f5ec',id,'Stir-Fried Basil with Deep-Fried Eggplant','stir-fried-basil-with-deep-fried-eggplant','Garlic, chilli, onion, eggplant, capsicum, zucchini, broccoli and basil.','PUBLISHED',true,28 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f65f9ce1-e4aa-5a5e-a308-b19d6f2406df','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Beef',2390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8521a52a-9401-595b-8b73-0d7a46c62f29','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Chicken',2390,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e8391464-c129-5bee-a500-bd898e9bbc15','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Vegetables & Tofu',2390,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1145b9d3-c507-5542-9941-539578a12659','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Prawns',2990,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e9456a98-2bc2-5c54-b513-c38f6ad756f4','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Seafood',3190,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9d56e9ed-e83e-5222-ae53-14139899923d','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Crispy Pork',2890,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a87811bc-131c-5ada-80f3-876339fe96c1','7710d8ce-7ae9-5941-845a-ad9b9451f5ec','Fish',2990,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','7710d8ce-7ae9-5941-845a-ad9b9451f5ec',28);

-- Printed entry 29: Sizzling Hot Plate
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '940bea1c-7096-50dd-ac50-d2e7032630a2',id,'Sizzling Hot Plate','sizzling-hot-plate','Onion, capsicum, zucchini, broccoli, cashew nuts and spring onion. Price pending confirmation.','PUBLISHED',false,29 FROM menu_category WHERE slug='stir-fries';
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','940bea1c-7096-50dd-ac50-d2e7032630a2',29);

-- Printed entry 30: Grilled Marinated Chicken
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'd76cbbcd-45a0-5e9d-a852-1fd25f0a9e63',id,'Grilled Marinated Chicken','grilled-marinated-chicken','Tender chicken fillet marinated in Thai spices and BBQ on a real Thai traditional grill.','PUBLISHED',true,30 FROM menu_category WHERE slug='bbq-grilled';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a57952c7-80e8-51e1-9063-38645dd22578','d76cbbcd-45a0-5e9d-a852-1fd25f0a9e63','Standard',2490,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','d76cbbcd-45a0-5e9d-a852-1fd25f0a9e63',30);

-- Printed entry 31: Grilled Marinated Pork
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'eb7b5329-004d-575b-a564-459889334107',id,'Grilled Marinated Pork','grilled-marinated-pork','Pork neck fillet marinated in Thai spices and BBQ on a real Thai traditional grill.','PUBLISHED',true,31 FROM menu_category WHERE slug='bbq-grilled';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dc3fe75c-76e7-5733-8265-ac973981fc10','eb7b5329-004d-575b-a564-459889334107','Standard',2590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','eb7b5329-004d-575b-a564-459889334107',31);

-- Printed entry 32: Grilled Marinated Beef (350g)
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'c4f2b8eb-ca8c-5a36-b5e7-e6326c6ae482',id,'Grilled Marinated Beef (350g)','grilled-marinated-beef-350g','Scotch fillet steak lightly marinated with special sauce, served with salad and chilli dipping sauce.','PUBLISHED',true,32 FROM menu_category WHERE slug='bbq-grilled';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('03c3aa4d-f55b-540f-8ece-14c32b504758','c4f2b8eb-ca8c-5a36-b5e7-e6326c6ae482','Standard',2890,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','c4f2b8eb-ca8c-5a36-b5e7-e6326c6ae482',32);

-- Printed entry 33: Pad Thai
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '1ee2e4cc-f14a-57a2-812c-228d9233909d',id,'Pad Thai','pad-thai','Stir-fried rice noodles with egg, tofu, bean sprouts and crushed peanuts.','PUBLISHED',true,33 FROM menu_category WHERE slug='noodles';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('943b76fd-56ee-5fe2-8717-d01dd095ecec','1ee2e4cc-f14a-57a2-812c-228d9233909d','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1bc7dbdd-c931-5d4a-870f-a1a75b876f20','1ee2e4cc-f14a-57a2-812c-228d9233909d','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('51d60aa9-1063-5e83-ae81-911c4fa3db71','1ee2e4cc-f14a-57a2-812c-228d9233909d','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e1061648-98a5-5ffd-987f-bdd16f1e79da','1ee2e4cc-f14a-57a2-812c-228d9233909d','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('787ce532-6304-5639-8639-235c38c72e4d','1ee2e4cc-f14a-57a2-812c-228d9233909d','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('32bf957c-a153-5c1c-bd22-871e7882f33f','1ee2e4cc-f14a-57a2-812c-228d9233909d','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('44819624-f133-5b68-a3c6-7b18465cd934','1ee2e4cc-f14a-57a2-812c-228d9233909d','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','1ee2e4cc-f14a-57a2-812c-228d9233909d',33);

-- Printed entry 34: Pad See Ew
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '9883d390-351d-59ac-a5c3-7643c556a857',id,'Pad See Ew','pad-see-ew','Stir-fried flat noodles with egg and Chinese broccoli.','PUBLISHED',true,34 FROM menu_category WHERE slug='noodles';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2718b37b-4def-539b-a982-2bd9c8dbd988','9883d390-351d-59ac-a5c3-7643c556a857','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ab419f57-a615-5b03-ac85-84168544779f','9883d390-351d-59ac-a5c3-7643c556a857','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e237772c-e511-5483-8330-965e319f0048','9883d390-351d-59ac-a5c3-7643c556a857','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('fd5a4da7-8454-53c9-b84a-056c301debee','9883d390-351d-59ac-a5c3-7643c556a857','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('16ab767e-43b4-5eb6-96c4-9468a771c3de','9883d390-351d-59ac-a5c3-7643c556a857','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1cfc1831-97f5-529b-b07d-64dba3b179d5','9883d390-351d-59ac-a5c3-7643c556a857','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9885658f-dc19-5321-988e-22c76ef95eb1','9883d390-351d-59ac-a5c3-7643c556a857','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','9883d390-351d-59ac-a5c3-7643c556a857',34);

-- Printed entry 35: Pad Kee Mao
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'e6df8824-59e0-5603-8d44-9bfbfbd25785',id,'Pad Kee Mao','pad-kee-mao','Stir-fried thick rice noodles with vegetables, fresh chilli and Thai basil.','PUBLISHED',true,35 FROM menu_category WHERE slug='noodles';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3dfcba05-1dcb-5f74-9904-d0a6ba5b24d3','e6df8824-59e0-5603-8d44-9bfbfbd25785','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e3cd5cea-3439-5d90-b67c-bdb1eb078992','e6df8824-59e0-5603-8d44-9bfbfbd25785','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d2de314a-1454-5096-a38b-71464eb98400','e6df8824-59e0-5603-8d44-9bfbfbd25785','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4178e490-43a8-50eb-8375-b059a5a06207','e6df8824-59e0-5603-8d44-9bfbfbd25785','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('43c4046b-60f0-5804-9db0-71ff2a7fcc83','e6df8824-59e0-5603-8d44-9bfbfbd25785','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('60c2fa9e-ee2c-5ea4-9e99-5ab3f35b4e01','e6df8824-59e0-5603-8d44-9bfbfbd25785','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c8c02848-9722-5dc6-abbe-1b6a63588815','e6df8824-59e0-5603-8d44-9bfbfbd25785','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','e6df8824-59e0-5603-8d44-9bfbfbd25785',35);

-- Printed entry 36: Egg Noodles with BBQ Chicken
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '54fc0cd2-80a2-5422-9be8-8cac57c56a89',id,'Egg Noodles with BBQ Chicken','egg-noodles-with-bbq-chicken','Egg noodles with Chinese broccoli and bean sprouts, topped with BBQ chicken.','PUBLISHED',true,36 FROM menu_category WHERE slug='noodles';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d8a47369-db1f-5ef9-9963-7edc754166ef','54fc0cd2-80a2-5422-9be8-8cac57c56a89','Standard',2190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','54fc0cd2-80a2-5422-9be8-8cac57c56a89',36);

-- Printed entry 37: Fried Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'e9026e19-bac1-5827-912e-db1251547cbf',id,'Fried Rice','fried-rice','With egg, onion, bok choy, carrot and broccoli.','PUBLISHED',true,37 FROM menu_category WHERE slug='rice';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5cb83f05-071c-5674-aaba-f180d497a032','e9026e19-bac1-5827-912e-db1251547cbf','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('45888c11-d844-52ef-8b08-614ea10da190','e9026e19-bac1-5827-912e-db1251547cbf','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('72790328-73d3-5d91-8600-76a2421e7ea5','e9026e19-bac1-5827-912e-db1251547cbf','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1d6d4638-1925-5356-b55c-94e46d6dedac','e9026e19-bac1-5827-912e-db1251547cbf','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('63767d95-f77a-5b10-9455-a18da41b1e7e','e9026e19-bac1-5827-912e-db1251547cbf','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4ed67c4f-f381-5922-b89a-baea6380ad96','e9026e19-bac1-5827-912e-db1251547cbf','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('71827c7b-6041-5e77-aa38-760fa08e0ec2','e9026e19-bac1-5827-912e-db1251547cbf','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','e9026e19-bac1-5827-912e-db1251547cbf',37);

-- Printed entry 38: Chilli Fried Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '4dee8e0a-f15b-595c-94fc-f9d83162d2af',id,'Chilli Fried Rice','chilli-fried-rice','With egg, onion, bok choy, carrot, broccoli and fresh chilli.','PUBLISHED',true,38 FROM menu_category WHERE slug='rice';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('29aa91b0-5e9a-5968-908f-6cced2ec96fc','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('76e4a258-eea6-54a7-95ea-bd909b8468d4','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dec7ad41-71df-55f7-ac22-a5d61cf5ead1','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('36119ec8-db18-54e1-a6ae-c9c7570a2836','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d608aae7-c746-5375-a7a4-88e75fe845b9','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('36f48b38-5bed-5671-9261-51f216573d4b','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d90423a0-dfa1-5451-9fdb-2a940e330d0d','4dee8e0a-f15b-595c-94fc-f9d83162d2af','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','4dee8e0a-f15b-595c-94fc-f9d83162d2af',38);

-- Printed entry 39: Tom Yum Fried Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '5f1f1232-bbd0-5f82-ac78-751100e27223',id,'Tom Yum Fried Rice','tom-yum-fried-rice','Hot and spicy rice stir-fried with Tom Yum flavours.','PUBLISHED',true,39 FROM menu_category WHERE slug='rice';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d4c0d5a2-b7e8-5c94-a9b9-83b4b49f5539','5f1f1232-bbd0-5f82-ac78-751100e27223','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('260b6a23-25bf-5e68-ab37-5c52367a9195','5f1f1232-bbd0-5f82-ac78-751100e27223','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('7835b63d-b4fb-5238-9d7e-ae3d36795dd4','5f1f1232-bbd0-5f82-ac78-751100e27223','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1790d317-8b0c-58a3-bd5f-d1ad04c0d01c','5f1f1232-bbd0-5f82-ac78-751100e27223','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('91834ec3-3f9e-58e3-8947-06b4a8b0cf48','5f1f1232-bbd0-5f82-ac78-751100e27223','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('876cb830-bfe3-5f98-bfc1-f2e163ff5d07','5f1f1232-bbd0-5f82-ac78-751100e27223','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('10084d61-aa31-5871-8fc9-235f11addab5','5f1f1232-bbd0-5f82-ac78-751100e27223','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','5f1f1232-bbd0-5f82-ac78-751100e27223',39);

-- Printed entry 40: Pineapple Fried Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '05ae3878-99bb-590b-8af4-fb0b6f479048',id,'Pineapple Fried Rice','pineapple-fried-rice','Fried rice with pineapple.','PUBLISHED',true,40 FROM menu_category WHERE slug='rice';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('13a5444c-ee66-5078-a26d-d21736e08f0f','05ae3878-99bb-590b-8af4-fb0b6f479048','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('59c4c2e9-9004-5bed-b4fc-fc7d27383b26','05ae3878-99bb-590b-8af4-fb0b6f479048','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('152f572a-3c3d-5628-b7a2-31b439f0d625','05ae3878-99bb-590b-8af4-fb0b6f479048','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ec590841-7aad-5214-b071-d750618be872','05ae3878-99bb-590b-8af4-fb0b6f479048','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8d444b48-b5aa-507e-a833-4d1d2145b004','05ae3878-99bb-590b-8af4-fb0b6f479048','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('396371a9-3c75-51dd-8d23-d96c3764334f','05ae3878-99bb-590b-8af4-fb0b6f479048','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f4521068-19ad-5d45-9182-b7c292097e33','05ae3878-99bb-590b-8af4-fb0b6f479048','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','05ae3878-99bb-590b-8af4-fb0b6f479048',40);

-- Printed entry 41: Chicken Noodle Soup
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '53cf702e-d1d6-5609-abb3-481ed869142e',id,'Chicken Noodle Soup','chicken-noodle-soup','Chicken with bean sprouts, Chinese broccoli and rice noodles, topped with garnish.','PUBLISHED',true,41 FROM menu_category WHERE slug='noodle-soup';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a3ba5025-c37c-5405-a245-e11bf990b062','53cf702e-d1d6-5609-abb3-481ed869142e','Standard',2090,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','53cf702e-d1d6-5609-abb3-481ed869142e',41);

-- Printed entry 42: Tom Yum Seafood Noodles Soup
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '3d1cf8c1-e6f3-5319-be2d-df9d73740aa1',id,'Tom Yum Seafood Noodles Soup','tom-yum-seafood-noodles-soup','Mixed seafood in Tom Yum soup with bean sprouts, mushroom and rice noodles, topped with garnish.','PUBLISHED',true,42 FROM menu_category WHERE slug='noodle-soup';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('431f9049-1750-5f3c-8f6b-7a06003306c9','3d1cf8c1-e6f3-5319-be2d-df9d73740aa1','Standard',2590,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','3d1cf8c1-e6f3-5319-be2d-df9d73740aa1',42);

-- Printed entry 43: Tom Yum Soup
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '77ee6fc4-2938-53c2-8fe4-93ba88fb23ea',id,'Tom Yum Soup','tom-yum-soup','Hot and sour soup with chopped galangal, lemongrass, kaffir lime leaf, lemon juice, onion, tomato and mushroom.','PUBLISHED',true,43 FROM menu_category WHERE slug='soups';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3a80157a-e402-53d9-bccd-9d1fdcdf11f5','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5fa254e2-408f-5ae5-a5c6-1007006edf30','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('407a3e11-d1fa-51a7-b899-8f43f920627f','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('05de59ab-0638-5287-9b32-0cbefc6d33b7','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('77e281ac-135e-5580-a65c-b910223427de','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('881ad072-5bfd-5c04-8648-f04c874cba93','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3af6d1df-87c6-5ada-bc52-7907f589695e','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','77ee6fc4-2938-53c2-8fe4-93ba88fb23ea',43);

-- Printed entry 44: Tom Kha Kai
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '1c43410f-bba7-5ec0-92a9-e2fad8862b51',id,'Tom Kha Kai','tom-kha-kai','Light coconut milk soup with galangal, lemongrass, kaffir lime leaf, lemon juice, chilli, onion, tomato and mushroom.','PUBLISHED',true,44 FROM menu_category WHERE slug='soups';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('28d4cb15-be69-5ae9-a28d-6eef6133be11','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Beef',2090,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1a8b6a68-368a-5fa8-b428-f28a99581248','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Chicken',2090,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('61cb5a2a-c800-5c7b-9deb-f7d2f4347291','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Vegetables & Tofu',2090,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b938aa48-e91b-5d3c-ad97-8c02ec32a719','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Prawns',2690,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b8f4acb0-cede-5ce6-803e-1408f1cce1fd','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Seafood',2890,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4b47d860-1285-5436-96de-55b15182164e','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Crispy Pork',2590,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dd3f016c-9b30-5e64-b524-f79fe8c1554f','1c43410f-bba7-5ec0-92a9-e2fad8862b51','Fish',2690,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','1c43410f-bba7-5ec0-92a9-e2fad8862b51',44);

-- Printed entry 45: Chicken / Beef Salad
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '3f78a703-fda1-5355-9bd4-86a41f948f2d',id,'Chicken / Beef Salad','chicken-beef-salad','BBQ chicken or sliced beef with fresh lime, chilli, tomato, cucumber, onion, coriander and spring onion.','PUBLISHED',true,45 FROM menu_category WHERE slug='salads';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b8972af2-ca38-5f97-9bea-da452c607719','3f78a703-fda1-5355-9bd4-86a41f948f2d','Chicken',2190,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c212a80f-568a-5866-86d5-289869347bf0','3f78a703-fda1-5355-9bd4-86a41f948f2d','Beef',2190,'AUD',false,2);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','3f78a703-fda1-5355-9bd4-86a41f948f2d',45);

-- Printed entry 46: Deep-Fried Tofu Salad
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'b76fcdfc-60a4-5507-9da0-9921257ba556',id,'Deep-Fried Tofu Salad','deep-fried-tofu-salad','Deep-fried tofu with fresh lime, chilli, tomato, cucumber, onion, coriander and spring onion. Printed menu label: VG, V.','PUBLISHED',true,46 FROM menu_category WHERE slug='salads';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ddfc255d-3bb6-5546-a6ee-16a70255810c','b76fcdfc-60a4-5507-9da0-9921257ba556','Standard',1890,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','b76fcdfc-60a4-5507-9da0-9921257ba556',46);

-- Printed entry 47: Larb Salad
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '5161ee85-5c8a-5a72-9b52-419a889232be',id,'Larb Salad','larb-salad','Minced chicken with chilli flakes, fresh lemon juice, red onion, mint, coriander and ground roasted rice.','PUBLISHED',true,47 FROM menu_category WHERE slug='salads';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2914d966-c993-5dfd-a51e-ec8aa930c51b','5161ee85-5c8a-5a72-9b52-419a889232be','Standard',2190,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','5161ee85-5c8a-5a72-9b52-419a889232be',47);

-- Printed entry 48: Coconut Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'cc9a3ece-3980-5d86-93f8-66b6a9349e74',id,'Coconut Rice','coconut-rice','Coconut Rice.','PUBLISHED',true,48 FROM menu_category WHERE slug='sides';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('131b3f95-65c9-5b2f-a6a5-e35303685a81','cc9a3ece-3980-5d86-93f8-66b6a9349e74','Standard',750,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','cc9a3ece-3980-5d86-93f8-66b6a9349e74',48);

-- Printed entry 49: Steamed Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'd4e71aa1-f730-5963-b69e-f0865b0126ff',id,'Steamed Rice','steamed-rice','Steamed Rice.','PUBLISHED',true,49 FROM menu_category WHERE slug='sides';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1874d544-c924-55d3-9eee-d7c834526f69','d4e71aa1-f730-5963-b69e-f0865b0126ff','Standard',650,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','d4e71aa1-f730-5963-b69e-f0865b0126ff',49);

-- Printed entry 50: Roti
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'f810954f-d0ea-52c1-99cb-f5586bf5bdcf',id,'Roti','roti','Roti.','PUBLISHED',true,50 FROM menu_category WHERE slug='sides';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('569b11d8-e951-57e3-8a7c-b4f258d190fb','f810954f-d0ea-52c1-99cb-f5586bf5bdcf','Standard',380,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','f810954f-d0ea-52c1-99cb-f5586bf5bdcf',50);

-- Printed entry 51: Home Made Peanut Sauce
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '4e39f40d-0cc3-5be4-91ce-90bf20bc82fe',id,'Home Made Peanut Sauce','home-made-peanut-sauce','Home Made Peanut Sauce.','PUBLISHED',true,51 FROM menu_category WHERE slug='sides';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dd130fb6-3c6a-5aa9-82d8-00576e4979f0','4e39f40d-0cc3-5be4-91ce-90bf20bc82fe','Standard',380,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','4e39f40d-0cc3-5be4-91ce-90bf20bc82fe',51);

-- Printed entry 52: Steamed Mixed Vegetables
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '8f0c0ffe-6667-5769-8471-2e4dc3785675',id,'Steamed Mixed Vegetables','steamed-mixed-vegetables','Steamed Mixed Vegetables.','PUBLISHED',true,52 FROM menu_category WHERE slug='sides';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('bd389868-ad2c-5d37-a91a-80588706b0fd','8f0c0ffe-6667-5769-8471-2e4dc3785675','Standard',790,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','8f0c0ffe-6667-5769-8471-2e4dc3785675',52);

-- Printed entry 53: Banana Dumplings
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '9ed5418d-aa5c-5b98-8c0a-61d24923de6b',id,'Banana Dumplings','banana-dumplings','Banana Dumplings.','PUBLISHED',true,53 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c6dc7124-0ee5-5d44-877b-6ddaf0e0a025','9ed5418d-aa5c-5b98-8c0a-61d24923de6b','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','9ed5418d-aa5c-5b98-8c0a-61d24923de6b',53);

-- Printed entry 54: Kanom Tuay
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '72dfc5a0-9947-58f5-acc2-6f9108fa2ca2',id,'Kanom Tuay','kanom-tuay','Kanom Tuay.','PUBLISHED',true,54 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('656baa64-acba-5396-a12f-d09dc03cda41','72dfc5a0-9947-58f5-acc2-6f9108fa2ca2','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','72dfc5a0-9947-58f5-acc2-6f9108fa2ca2',54);

-- Printed entry 55: Banana Fritter
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '2b7fbe61-9096-53ee-99e5-82db6f8c4def',id,'Banana Fritter','banana-fritter','Banana Fritter.','PUBLISHED',true,55 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4b06e094-b951-5dc9-a8f7-34348e50b13d','2b7fbe61-9096-53ee-99e5-82db6f8c4def','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','2b7fbe61-9096-53ee-99e5-82db6f8c4def',55);

-- Printed entry 56: Taro Dumpling
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '679651ea-b06f-5019-9e10-7be25e3a5633',id,'Taro Dumpling','taro-dumpling','Taro Dumpling.','PUBLISHED',true,56 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d4299edf-6fa1-570d-a5c2-1cfaa3638ac9','679651ea-b06f-5019-9e10-7be25e3a5633','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','679651ea-b06f-5019-9e10-7be25e3a5633',56);

-- Printed entry 57: Sticky Date Pudding
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '7c99f53b-b01e-5773-abc1-5855764b6f53',id,'Sticky Date Pudding','sticky-date-pudding','Sticky Date Pudding.','PUBLISHED',true,57 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f7b59fcb-8316-5ab4-b6bb-c37662d9cb76','7c99f53b-b01e-5773-abc1-5855764b6f53','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','7c99f53b-b01e-5773-abc1-5855764b6f53',57);

-- Printed entry 58: Mixed Berries Crepes
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '4db987df-ddbb-5aca-9cb0-6318f2ada804',id,'Mixed Berries Crepes','mixed-berries-crepes','Mixed Berries Crepes.','PUBLISHED',true,58 FROM menu_category WHERE slug='desserts';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f10beb13-8c8a-53eb-964b-0b2a08a061b8','4db987df-ddbb-5aca-9cb0-6318f2ada804','Standard',1290,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('100e2aea-864f-5987-bc38-254721d4acee','4db987df-ddbb-5aca-9cb0-6318f2ada804',58);

-- Printed entry L1: Pad Thai
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '6841e08f-b7c2-51ca-81d5-439fdff05d43',id,'Pad Thai','lunch-pad-thai','Rice noodles with egg, tofu and bean sprouts. Lunch special till 2:30 PM.','PUBLISHED',false,59 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('bc48f648-668a-5a39-be75-3702a76314d3','6841e08f-b7c2-51ca-81d5-439fdff05d43','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('54a7393e-0eca-5f19-8c36-5e38c2347465','6841e08f-b7c2-51ca-81d5-439fdff05d43','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0a143647-9d6c-5df9-8d57-3aa61c62ed60','6841e08f-b7c2-51ca-81d5-439fdff05d43','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d06936e2-3dcc-55e3-b42a-870b568f8a37','6841e08f-b7c2-51ca-81d5-439fdff05d43','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('99d8cb0f-ef9d-5ced-9132-2e27d788337c','6841e08f-b7c2-51ca-81d5-439fdff05d43','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9d2ecb46-c517-5d2b-8f59-33e3a5afe587','6841e08f-b7c2-51ca-81d5-439fdff05d43','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b39130a3-9be1-583f-a26d-5d9d3c0b26a2','6841e08f-b7c2-51ca-81d5-439fdff05d43','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','6841e08f-b7c2-51ca-81d5-439fdff05d43',59);

-- Printed entry L2: Pad See Ew
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '9ee28ae0-5a10-5ec8-9b68-b481d3faffa0',id,'Pad See Ew','lunch-pad-see-ew','Stir-fried flat noodles with egg and Chinese broccoli. Lunch special till 2:30 PM.','PUBLISHED',false,60 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e77db164-06e0-55b1-8a9e-a746cd3b17ee','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6b145448-c059-588f-9627-06c998316a2d','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('fd362a8c-0494-50ec-a5d4-8d4db98db2a0','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0c5f7719-429d-5578-96c2-4bd619097868','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('48638b8c-446e-5396-987f-a3d9ae88fc56','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c3883360-109f-5a86-bd99-bb3346d8a20a','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f88347a0-b918-5c5f-8f7d-52a684123afb','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','9ee28ae0-5a10-5ec8-9b68-b481d3faffa0',60);

-- Printed entry L3: Pad Kee Mao
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '7090f016-5f62-5b59-9cc1-33b660f6b560',id,'Pad Kee Mao','lunch-pad-kee-mao','Stir-fried thick rice noodles, vegetables, fresh chilli and Thai basil. Lunch special till 2:30 PM.','PUBLISHED',false,61 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5c1fd04a-3e5a-5526-bea6-6b54eff3f5d3','7090f016-5f62-5b59-9cc1-33b660f6b560','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2010b7dc-edc3-5b3d-9531-df982b4ec5b8','7090f016-5f62-5b59-9cc1-33b660f6b560','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('cf5e1017-09ad-55e2-a920-b3c13b98e9cb','7090f016-5f62-5b59-9cc1-33b660f6b560','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f618cad5-e96d-58d1-b625-b4c57f73b47d','7090f016-5f62-5b59-9cc1-33b660f6b560','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('07f83bb2-634a-5ce1-a9c5-8d87af3ed9bb','7090f016-5f62-5b59-9cc1-33b660f6b560','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6c92f318-6e68-5603-b327-d455e1c9f2fb','7090f016-5f62-5b59-9cc1-33b660f6b560','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4cf7d7e9-bf15-5146-9a7f-d65d0f1f5ffa','7090f016-5f62-5b59-9cc1-33b660f6b560','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','7090f016-5f62-5b59-9cc1-33b660f6b560',61);

-- Printed entry L4: Fried Rice Chicken / Beef
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '8aadd732-f6f8-5def-ae8e-9dd6007f570a',id,'Fried Rice Chicken / Beef','lunch-fried-rice-chicken-beef','Rice, egg, onion, bok choy, carrot and broccoli. Lunch special till 2:30 PM. Printed menu label: GF.','PUBLISHED',false,62 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8d6deb50-4711-52c2-9d9a-4cf4a7c03f13','8aadd732-f6f8-5def-ae8e-9dd6007f570a','Chicken',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('cb8c0728-08c7-5eb6-827c-cdfdb882dc67','8aadd732-f6f8-5def-ae8e-9dd6007f570a','Beef',1490,'AUD',false,2);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','8aadd732-f6f8-5def-ae8e-9dd6007f570a',62);

-- Printed entry L5: Green Curry with Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '56d3254b-b9a0-5c88-b44e-d666338d2ceb',id,'Green Curry with Rice','lunch-green-curry-with-rice','Pumpkin, green beans, zucchini, capsicum, bamboo and basil. Lunch special till 2:30 PM. Printed menu label: GF.','PUBLISHED',false,63 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b22407a9-2491-58d2-86a8-ee07955950db','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c693282d-624a-5388-ba00-50a20c3a3c9f','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('823a7037-af29-5f1b-b569-9239f2ad43b1','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a724984b-1ee2-5ec5-8cca-5e82759b5e32','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4d5aba2d-ea7f-5990-89c5-0c15b9799139','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('70d4506b-c135-5e8f-9e23-5e161b349480','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('6a9b1cb6-468e-5708-9ae7-a636f1634d8d','56d3254b-b9a0-5c88-b44e-d666338d2ceb','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','56d3254b-b9a0-5c88-b44e-d666338d2ceb',63);

-- Printed entry L6: Red Curry with Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT 'cf478569-e9dc-5d5a-aa24-fcaf7d434895',id,'Red Curry with Rice','lunch-red-curry-with-rice','Pumpkin, green beans, zucchini, capsicum, carrot, bamboo and basil. Lunch special till 2:30 PM. Printed menu label: GF.','PUBLISHED',false,64 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e586c31b-cf09-5383-9497-85bd71a141dd','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('de06a555-ae71-5c51-a8d4-8ae59e0f105d','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4aa091a4-ce7a-57c3-b50a-766578a29cf2','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('606cdcf4-0c04-5d29-9201-ebc5cfa5023f','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a96296a6-b825-587f-b966-639cc027f847','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('01d78f89-ab8d-586f-96fb-5a49b6dfebb3','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d79da2cb-6961-596a-a350-cd6b8002fed1','cf478569-e9dc-5d5a-aa24-fcaf7d434895','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','cf478569-e9dc-5d5a-aa24-fcaf7d434895',64);

-- Printed entry L7: Yellow Curry with Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '8b5166d2-1914-546d-a720-0e3de081962e',id,'Yellow Curry with Rice','lunch-yellow-curry-with-rice','Potatoes, carrot and onion served with rice. Lunch special till 2:30 PM. Printed menu label: GF.','PUBLISHED',false,65 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('384d1bc5-1d33-5387-8ba4-9968f53a4feb','8b5166d2-1914-546d-a720-0e3de081962e','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ad4ac607-594a-553b-a703-9343a01e39d9','8b5166d2-1914-546d-a720-0e3de081962e','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('d40ddabe-fb6e-554f-9055-b17e49c16e16','8b5166d2-1914-546d-a720-0e3de081962e','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('bbc21544-332f-52d0-9f3a-daec500049c2','8b5166d2-1914-546d-a720-0e3de081962e','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b712b2d7-ab55-575e-a929-87f8b59079f0','8b5166d2-1914-546d-a720-0e3de081962e','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('04af89d2-fd56-5159-9e7d-9c4e1a321744','8b5166d2-1914-546d-a720-0e3de081962e','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('124cf7d4-4c36-5a32-a29b-2e25cfdb4068','8b5166d2-1914-546d-a720-0e3de081962e','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','8b5166d2-1914-546d-a720-0e3de081962e',65);

-- Printed entry L8: Pad Kra Pao with Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '50cb952e-43ae-5e7a-a818-3f55cd152940',id,'Pad Kra Pao with Rice','lunch-pad-kra-pao-with-rice','Garlic, chilli, onion, capsicum, zucchini, carrot, broccoli, green beans, bamboo shoot and basil. Lunch special till 2:30 PM.','PUBLISHED',false,66 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('a3d0b980-d86a-5e22-94d9-39c0d0480eda','50cb952e-43ae-5e7a-a818-3f55cd152940','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('259b2630-21b9-53a5-bbce-ab208fe05659','50cb952e-43ae-5e7a-a818-3f55cd152940','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('ec0f7a31-82e2-5388-aede-694f5f75b3fe','50cb952e-43ae-5e7a-a818-3f55cd152940','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('766aee09-5acb-56a9-bb36-e0871bb91b7d','50cb952e-43ae-5e7a-a818-3f55cd152940','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('83b8b0fb-6486-518a-a5a9-6fff5c77be0c','50cb952e-43ae-5e7a-a818-3f55cd152940','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4edfef1f-dec0-537b-899b-04ad5f953de1','50cb952e-43ae-5e7a-a818-3f55cd152940','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('7e325c0c-e1cc-5565-9779-7b1bb3efbd47','50cb952e-43ae-5e7a-a818-3f55cd152940','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','50cb952e-43ae-5e7a-a818-3f55cd152940',66);

-- Printed entry L9: Cashew Nut with Chilli Jam
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '760d06d9-97a8-5415-ac08-44f6aa2d364a',id,'Cashew Nut with Chilli Jam','lunch-cashew-nut-with-chilli-jam','Onion, capsicum, zucchini, broccoli, cashew nuts and spring onion, served with rice. Lunch special till 2:30 PM.','PUBLISHED',false,67 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f16cbc6d-8132-59c9-8619-05dc24bae3e7','760d06d9-97a8-5415-ac08-44f6aa2d364a','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('615bed3f-9b38-59b1-a1b0-6a33ce1edf1b','760d06d9-97a8-5415-ac08-44f6aa2d364a','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('2dc88f7b-17bf-5807-bc47-34fdd9e96230','760d06d9-97a8-5415-ac08-44f6aa2d364a','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('90a82c3e-07ae-5d93-b203-fa36c03a8e94','760d06d9-97a8-5415-ac08-44f6aa2d364a','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c689c8af-25cb-5617-96ea-f9defcbc7b67','760d06d9-97a8-5415-ac08-44f6aa2d364a','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e4e7ced6-1cbf-5f43-a92c-ba37d3af5e9b','760d06d9-97a8-5415-ac08-44f6aa2d364a','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('1670fc73-6a4c-5bf6-a9d6-a5e56d7aabac','760d06d9-97a8-5415-ac08-44f6aa2d364a','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','760d06d9-97a8-5415-ac08-44f6aa2d364a',67);

-- Printed entry L10: Ginger Stir Fried with Rice
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '5c5c57bb-ed2c-5000-93ac-6bc070dd14de',id,'Ginger Stir Fried with Rice','lunch-ginger-stir-fried-with-rice','Broccoli, carrot, zucchini, snow peas, garlic, onion and black pepper, served with rice. Lunch special till 2:30 PM.','PUBLISHED',false,68 FROM menu_category WHERE slug='lunch';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('16d10911-e4c8-56a3-bfc8-60d3e6a8a246','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Beef',1490,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('dfdb660d-c61a-5b2d-a596-4be27fb97fbf','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Chicken',1490,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('df743d2d-8ab2-5ce6-bd46-7cfe212fb2f6','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Vegetables & Tofu',1490,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('90049ee8-2e0d-509b-9e2b-6289e78f59f9','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Prawns',2090,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5c65adc2-5ad0-5833-9fdb-814e2dfc7564','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Seafood',2290,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('4948d22a-b73f-57bc-b333-7f656cc40216','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Crispy Pork',1990,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('5358da1f-cdeb-5836-a0ca-071169061799','5c5c57bb-ed2c-5000-93ac-6bc070dd14de','Fish',2090,'AUD',false,7);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('afda33df-467c-5762-b741-1796d896af69','5c5c57bb-ed2c-5000-93ac-6bc070dd14de',68);

-- Printed entry D1: Thai Tea & Coffee
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '0900e0c8-6d29-56d2-a489-5875357491e5',id,'Thai Tea & Coffee','thai-tea-coffee','Choice of iced drink.','PUBLISHED',true,69 FROM menu_category WHERE slug='drinks';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('06b207fc-6bf8-56a5-a24d-cbefe9e15829','0900e0c8-6d29-56d2-a489-5875357491e5','Iced Black Coffee',999,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('71471b24-bc45-5018-8211-25f535aba1f6','0900e0c8-6d29-56d2-a489-5875357491e5','Iced Milk Coffee',999,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('96e3f82f-36bf-5ed3-a804-409747853290','0900e0c8-6d29-56d2-a489-5875357491e5','Thai Milk Tea',999,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('f611fe10-2e5f-5b98-a94a-d8917ff0b3b0','0900e0c8-6d29-56d2-a489-5875357491e5','Iced Lemon Tea',999,'AUD',false,4);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','0900e0c8-6d29-56d2-a489-5875357491e5',69);

-- Printed entry D2: Juices
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '94be512e-af3b-55e8-9f78-c719a6cec90d',id,'Juices','juices','Choice of juice.','PUBLISHED',true,70 FROM menu_category WHERE slug='drinks';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9e4e008f-8c64-571e-b054-ba754a9ddaab','94be512e-af3b-55e8-9f78-c719a6cec90d','Apple',499,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e255be2d-3238-51c0-86da-ae81bebaec35','94be512e-af3b-55e8-9f78-c719a6cec90d','Orange',499,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('48153d23-db41-531c-aa6d-726a4e4522be','94be512e-af3b-55e8-9f78-c719a6cec90d','Pineapple',499,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c5a0b33c-3bdb-5597-a5ff-3c4f1ad56824','94be512e-af3b-55e8-9f78-c719a6cec90d','Cranberry',499,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9b90d4e8-28ad-55d3-8e61-92d63e08c92e','94be512e-af3b-55e8-9f78-c719a6cec90d','Tomato',499,'AUD',false,5);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','94be512e-af3b-55e8-9f78-c719a6cec90d',70);

-- Printed entry D3: Soft Drinks
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '777c24dd-4987-5365-86c1-a9b30b0eac53',id,'Soft Drinks','soft-drinks','Choice of soft drink.','PUBLISHED',true,71 FROM menu_category WHERE slug='drinks';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0a439bdd-a00b-54b4-95a8-1d5d8d661a78','777c24dd-4987-5365-86c1-a9b30b0eac53','Coke',399,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('c4d27352-dba5-5bd0-9e35-77486079e5d4','777c24dd-4987-5365-86c1-a9b30b0eac53','Coke Zero',399,'AUD',false,2);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('8bb3fef5-673d-5be8-8f92-534edc1cee89','777c24dd-4987-5365-86c1-a9b30b0eac53','Fanta',399,'AUD',false,3);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('040877d8-2fcf-5750-8289-b6ce2b2c6bb7','777c24dd-4987-5365-86c1-a9b30b0eac53','Lemonade',399,'AUD',false,4);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('149b0ef2-c943-53e9-8baa-e98ae8c483f6','777c24dd-4987-5365-86c1-a9b30b0eac53','Sprite',399,'AUD',false,5);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e27448da-7505-5b5b-aa70-8e7a35b5e531','777c24dd-4987-5365-86c1-a9b30b0eac53','Lemon Squash',399,'AUD',false,6);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9ecd3c84-2c01-542c-90f0-3e6db1ec5a75','777c24dd-4987-5365-86c1-a9b30b0eac53','Ginger Beer',399,'AUD',false,7);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('caac8733-4640-5ed5-b894-db45b32853b8','777c24dd-4987-5365-86c1-a9b30b0eac53','Pepsi',399,'AUD',false,8);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('074139d0-244e-5e0f-92a1-b62fa2eb29a4','777c24dd-4987-5365-86c1-a9b30b0eac53','Pepsi Max',399,'AUD',false,9);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('836ba9f1-a9fa-5d9d-9620-6c11bcd1d8be','777c24dd-4987-5365-86c1-a9b30b0eac53','Solo',399,'AUD',false,10);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('16993216-f15d-5629-8565-0b041e7a44e6','777c24dd-4987-5365-86c1-a9b30b0eac53','Pasito',399,'AUD',false,11);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('9be3c27b-0dcb-5678-9c8d-3eb835b0ade2','777c24dd-4987-5365-86c1-a9b30b0eac53','Sunkist',399,'AUD',false,12);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('e9d50b60-249f-5d5a-8cde-80ee312ded98','777c24dd-4987-5365-86c1-a9b30b0eac53','Diet Coke',399,'AUD',false,13);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','777c24dd-4987-5365-86c1-a9b30b0eac53',71);

-- Printed entry D4: Sparkling Water
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '7584c997-7f84-5692-8804-1017928baff2',id,'Sparkling Water','sparkling-water','Choice of size.','PUBLISHED',true,72 FROM menu_category WHERE slug='drinks';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('3619ad2b-a714-56cc-84f6-316900462c79','7584c997-7f84-5692-8804-1017928baff2','Small',390,'AUD',false,1);
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('b343462c-b152-5950-ae17-17d18cab873c','7584c997-7f84-5692-8804-1017928baff2','Large',750,'AUD',false,2);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','7584c997-7f84-5692-8804-1017928baff2',72);

-- Printed entry D5: Coconut Water
INSERT INTO menu_item (id,category_id,name,slug,description,status,is_available,display_order) SELECT '2ff97ecf-de7a-5994-a62d-b4a8a9f8d30b',id,'Coconut Water','coconut-water','Coconut Water.','PUBLISHED',true,73 FROM menu_category WHERE slug='drinks';
INSERT INTO menu_item_variation (id,menu_item_id,name,price_minor,currency,is_default,display_order) VALUES ('0b8e9861-37ef-505d-aa74-1200b55874ec','2ff97ecf-de7a-5994-a62d-b4a8a9f8d30b','Standard',750,'AUD',true,1);
INSERT INTO menu_collection_item (collection_id,menu_item_id,display_order) VALUES ('73e1e438-d7da-5c30-ae4f-f2799cc76c87','2ff97ecf-de7a-5994-a62d-b4a8a9f8d30b',73);
