-- Starting configuration approved from the staff dashboard, 2026-09-25.
-- All TIME values are restaurant/collection LOCAL wall-clock times, never UTC.
-- Deploy with the direct LocalTime mappings in this change.
-- Existing schedules are retained. Only the exact captured development rows
-- affected by the legacy UTC-calendar conversion are corrected below.
-- Restaurant timezone is Australia/Melbourne; staff ordering defaults to unpaused
-- (already supplied by V21/V24). Existing settings and environment flags survive.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM restaurant_opening_hours) THEN
        INSERT INTO restaurant_opening_hours
            (id, day_of_week, opens_at, closes_at, is_active, display_order)
        VALUES
            ('4ecf86b3-4ba2-4420-9665-5f0cbe6a2d8b', 1, TIME '09:00', TIME '22:00', true, 0),
            ('012041c6-bed9-4056-be5d-486f3d9eb40f', 2, TIME '09:00', TIME '22:00', true, 0),
            ('d7a486c4-ea0d-414e-b4eb-4b26c6c4777f', 3, TIME '09:00', TIME '22:00', true, 0),
            ('3a23f6bc-4e58-4ed7-a626-436ddfee9312', 4, TIME '09:00', TIME '22:00', true, 0),
            ('1fa972fb-9259-4667-a153-1f8d0907fd67', 5, TIME '09:00', TIME '22:00', true, 0),
            ('19b5f56a-eef8-4164-96d7-3b87db02fc3c', 6, TIME '09:00', TIME '22:00', true, 0),
            ('bd268dc2-36e1-4605-bd55-87572b26a1c3', 7, TIME '09:00', TIME '22:00', true, 0);
    END IF;
END $$;

-- Correct only known captured opening windows, without changing later edits.
UPDATE restaurant_opening_hours h
SET opens_at = TIME '09:00', closes_at = TIME '22:00', version = h.version + 1
FROM (VALUES
    ('4ecf86b3-4ba2-4420-9665-5f0cbe6a2d8b'::uuid, 1),
    ('012041c6-bed9-4056-be5d-486f3d9eb40f'::uuid, 2),
    ('d7a486c4-ea0d-414e-b4eb-4b26c6c4777f'::uuid, 3),
    ('3a23f6bc-4e58-4ed7-a626-436ddfee9312'::uuid, 4),
    ('1fa972fb-9259-4667-a153-1f8d0907fd67'::uuid, 5),
    ('19b5f56a-eef8-4164-96d7-3b87db02fc3c'::uuid, 6),
    ('bd268dc2-36e1-4605-bd55-87572b26a1c3'::uuid, 7)) AS captured(id, day_of_week)
WHERE h.id = captured.id AND h.day_of_week = captured.day_of_week
  AND h.opens_at = TIME '23:00' AND h.closes_at = TIME '12:00'
  AND (SELECT timezone FROM restaurant_settings WHERE id = 1) = 'Australia/Melbourne';

-- V22 already supplies published/active Lunch Special and a 14:30 cutoff.
-- Correct its captured edited cutoff only; do not overwrite later configuration.
UPDATE menu_collection
SET daily_cutoff_time = TIME '14:30', version = version + 1
WHERE id = '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2'
  AND daily_cutoff_time = TIME '04:30' AND version = 4
  AND (SELECT timezone FROM restaurant_settings WHERE id = 1) = 'Australia/Melbourne';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM menu_collection WHERE slug = 'lunch-special')
       AND NOT EXISTS (SELECT 1 FROM menu_collection_schedule s
           JOIN menu_collection c ON c.id = s.collection_id WHERE c.slug = 'lunch-special') THEN
        INSERT INTO menu_collection_schedule
            (id, collection_id, rule_type, day_of_week, start_time, end_time, is_active, display_order)
        SELECT v.id, c.id, 'WEEKLY', v.day_of_week, TIME '11:00', v.end_time, true, v.display_order
        FROM menu_collection c CROSS JOIN (VALUES
            ('eadde765-5dfa-469e-876d-f724b0cc9a54'::uuid, 7, TIME '14:30', 0),
            ('4c7c3f17-1725-4051-98b8-2b89ad0a1357'::uuid, 1, TIME '14:30', 1),
            ('a72e5e51-971d-4305-93e8-b783c148cb44'::uuid, 2, TIME '14:30', 2),
            ('852c1e77-1364-4792-a77d-23bebc231b29'::uuid, 3, TIME '14:30', 3),
            ('3e3dc9b3-257e-4d9e-9b55-57c23752cb9c'::uuid, 4, TIME '14:30', 4),
            ('a3b829da-480e-419b-8105-0e3ae9c1e3cf'::uuid, 5, TIME '14:30', 5),
            ('b38a501d-bb02-45a1-a8f4-2b3f13f8f8eb'::uuid, 6, TIME '14:30', 6)) AS v(id, day_of_week, end_time, display_order)
        WHERE c.slug = 'lunch-special';
    END IF;
END $$;

-- Correct the captured Tuesday and Thursday finishes to 14:30.
UPDATE menu_collection_schedule s
SET start_time = TIME '11:00', end_time = captured.local_end, version = s.version + 1
FROM (VALUES
    ('eadde765-5dfa-469e-876d-f724b0cc9a54'::uuid, 7, TIME '04:30:00', TIME '14:30'),
    ('4c7c3f17-1725-4051-98b8-2b89ad0a1357'::uuid, 1, TIME '04:30:00', TIME '14:30'),
    ('a72e5e51-971d-4305-93e8-b783c148cb44'::uuid, 2, TIME '04:00:00', TIME '14:30'),
    ('852c1e77-1364-4792-a77d-23bebc231b29'::uuid, 3, TIME '04:30:00', TIME '14:30'),
    ('3e3dc9b3-257e-4d9e-9b55-57c23752cb9c'::uuid, 4, TIME '16:30:00', TIME '14:30'),
    ('a3b829da-480e-419b-8105-0e3ae9c1e3cf'::uuid, 5, TIME '04:30:00', TIME '14:30'),
    ('b38a501d-bb02-45a1-a8f4-2b3f13f8f8eb'::uuid, 6, TIME '04:30:00', TIME '14:30')) AS captured(id, day_of_week, stored_end, local_end)
WHERE s.id = captured.id AND s.day_of_week = captured.day_of_week
  AND s.collection_id = '8ed50da5-f6d9-54b3-9611-2bc33b7e54d2'
  AND s.rule_type = 'WEEKLY' AND s.start_time = TIME '01:00' AND s.end_time = captured.stored_end
  AND (SELECT timezone FROM menu_collection WHERE id = s.collection_id) = 'Australia/Melbourne';
