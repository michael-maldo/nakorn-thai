-- Historical collection and pricing snapshots for order lines.
-- Existing rows retain unknown provenance (version 0); do not infer it from
-- current menu memberships. New application writes must supply version 1 data.
-- collection_id intentionally has no foreign key: collection deletion must
-- not remove or invalidate historical order provenance.

ALTER TABLE public.restaurant_order_item
    ADD COLUMN snapshot_version smallint NOT NULL DEFAULT 0,
    ADD COLUMN collection_id uuid,
    ADD COLUMN collection_name varchar(150),
    ADD COLUMN collection_slug varchar(180),
    ADD COLUMN variation_base_price_minor bigint,
    ADD COLUMN collection_price_override_minor bigint;

-- This changes the default for future inserts only; existing rows remain 0.
-- Deploy with the new order writer: the old writer cannot supply these fields.
ALTER TABLE public.restaurant_order_item
    ALTER COLUMN snapshot_version SET DEFAULT 1;

ALTER TABLE public.restaurant_order_item
    ADD CONSTRAINT order_item_snapshot_shape_check CHECK (
        (
            snapshot_version = 0
            AND collection_id IS NULL
            AND collection_name IS NULL
            AND collection_slug IS NULL
            AND variation_base_price_minor IS NULL
            AND collection_price_override_minor IS NULL
        )
        OR
        (
            snapshot_version = 1
            AND collection_id IS NOT NULL
            AND collection_name IS NOT NULL
            AND btrim(collection_name) <> ''
            AND collection_slug IS NOT NULL
            AND btrim(collection_slug) <> ''
            AND variation_base_price_minor IS NOT NULL
            AND variation_base_price_minor >= 0
            AND (
                collection_price_override_minor IS NULL
                OR collection_price_override_minor >= 0
            )
        )
    ),
    ADD CONSTRAINT order_item_price_floor_check CHECK (
        snapshot_version = 0
        OR unit_price_minor >= COALESCE(
            collection_price_override_minor,
            variation_base_price_minor
        )
    );

CREATE INDEX restaurant_order_item_collection
    ON public.restaurant_order_item (collection_id)
    WHERE collection_id IS NOT NULL;

COMMENT ON COLUMN public.restaurant_order_item.snapshot_version IS
    '0: legacy line with unknown collection provenance; '
    '1: collection and base-price snapshots captured at checkout.';

COMMENT ON COLUMN public.restaurant_order_item.collection_id IS
    'Historical collection identifier captured at checkout; intentionally '
    'not a foreign key to the live menu collection.';

COMMENT ON COLUMN public.restaurant_order_item.collection_name IS
    'Collection display name captured at checkout; independent of later edits.';

COMMENT ON COLUMN public.restaurant_order_item.collection_slug IS
    'Collection slug captured at checkout; independent of later edits.';

COMMENT ON COLUMN public.restaurant_order_item.variation_base_price_minor IS
    'Selected variation price before any collection override or options, '
    'snapshotted at checkout in AUD minor units.';

COMMENT ON COLUMN public.restaurant_order_item.collection_price_override_minor IS
    'Collection override actually applied to the default/base variation, '
    'in AUD minor units; NULL means no override was applied.';

-- V17 option snapshots remain unchanged. Their nonnegative deltas justify
-- the price floor above. The order writer must enforce exact unit-price
-- equality with effective base plus selected option deltas per dish unit.
-- Option-group selection limits and schedule evaluation are application
-- rules and are outside this provenance migration.
