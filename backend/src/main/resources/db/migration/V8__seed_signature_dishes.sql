-- Existing public homepage content only. No prices or food-suitability claims
-- are inferred. Images are added after persistent media storage is configured.
INSERT INTO menu_category (id, name, slug, display_order) VALUES
('10000000-0000-0000-0000-000000000001', 'Curries', 'curries', 1),
('10000000-0000-0000-0000-000000000002', 'Stir-fries', 'stir-fries', 2);

INSERT INTO menu_item (id, category_id, name, slug, description, status, display_order) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
 'Yellow Curry', 'yellow-curry',
 'A fragrant, gently spiced curry served with steamed jasmine rice.', 'PUBLISHED', 1),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
 'Crispy Pork Stir-Fry', 'crispy-pork-stir-fry',
 'Crispy pork tossed with fresh seasonal vegetables and Thai sauce.', 'PUBLISHED', 1),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
 'Green Curry', 'green-curry',
 'Classic Thai green curry with bamboo shoots, vegetables and jasmine rice.', 'PUBLISHED', 2),
('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002',
 'Crispy Pork & Broccoli', 'crispy-pork-broccoli',
 'Crispy pork served over Chinese broccoli with a savoury garlic sauce.', 'PUBLISHED', 2);

INSERT INTO menu_collection (id, name, slug, status) VALUES
('30000000-0000-0000-0000-000000000001', 'Signature Dishes', 'signature-dishes', 'PUBLISHED');

INSERT INTO menu_collection_item (collection_id, menu_item_id, display_order) VALUES
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 1),
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 2),
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000003', 3),
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004', 4);
