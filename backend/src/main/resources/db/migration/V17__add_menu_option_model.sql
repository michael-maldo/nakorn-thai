-- V15__add_menu_option_model.sql
--
-- Adds reusable option groups/options for configurable menu items.
--
-- Examples supported by this model:
--   Green Curry -> Protein -> Chicken / Beef / Prawns (+600)
--   Barramundi  -> Sauce   -> Tamarind / Ginger / Sweet & Sour
--   Any item    -> Size    -> Small / Medium / Large
--
-- Multiple option groups can be attached to the same menu item, so choices
-- can be combined. Pricing in this version is additive:
--
--   final unit price =
--       menu_item_variation.price_minor
--       + SUM(selected menu_option.price_delta_minor * option quantity)
--
-- menu_item_variation remains the base purchasable variation/price so the
-- existing ordering model can continue to use a default variation.
--
-- restaurant_order_item_option stores a snapshot of chosen options so order
-- details remain readable even if option display names/prices later change.



-- -------------------------------------------------------------------------
-- Menu collection availability / scheduling
-- -------------------------------------------------------------------------
-- Collection lifecycle is controlled by status:
--   DRAFT / PUBLISHED / ARCHIVED
--
-- Operational availability is controlled by is_active.
--
-- Broad validity windows use the existing starts_at / ends_at columns.
-- Examples:
--   Summer Promo: starts_at = 2026-12-01, ends_at = 2027-02-28
--   Always-on menu: both NULL
--
-- Fine-grained recurring or specific-date availability is stored in
-- menu_collection_schedule.
--
-- The timezone column defines the local timezone used to interpret weekday
-- and time-of-day schedule rows. Do not evaluate restaurant schedules using
-- the VPS/system timezone.

ALTER TABLE public.menu_collection
    ADD COLUMN is_active boolean DEFAULT true NOT NULL,
    ADD COLUMN timezone varchar(64) DEFAULT 'Australia/Melbourne' NOT NULL;

CREATE TABLE public.menu_collection_schedule (
    id uuid NOT NULL,
    collection_id uuid NOT NULL,
    rule_type varchar(20) NOT NULL,
    day_of_week smallint,
    specific_date date,
    start_time time without time zone,
    end_time time without time zone,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,

    CONSTRAINT menu_collection_schedule_pkey PRIMARY KEY (id),

    CONSTRAINT menu_collection_schedule_collection_id_fkey
        FOREIGN KEY (collection_id)
        REFERENCES public.menu_collection(id)
        ON DELETE CASCADE,

    CONSTRAINT menu_collection_schedule_rule_type_check
        CHECK (rule_type IN ('WEEKLY', 'SPECIFIC_DATE')),

    CONSTRAINT menu_collection_schedule_day_of_week_check
        CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),

    CONSTRAINT menu_collection_schedule_display_order_check
        CHECK (display_order >= 0),

    CONSTRAINT menu_collection_schedule_version_check
        CHECK (version >= 0),

    CONSTRAINT menu_collection_schedule_rule_shape_check CHECK (
        (rule_type = 'WEEKLY'
            AND day_of_week IS NOT NULL
            AND specific_date IS NULL)
        OR
        (rule_type = 'SPECIFIC_DATE'
            AND specific_date IS NOT NULL
            AND day_of_week IS NULL)
    ),

    CONSTRAINT menu_collection_schedule_time_pair_check CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR
        (start_time IS NOT NULL AND end_time IS NOT NULL)
    )
);

CREATE INDEX menu_collection_schedule_collection
    ON public.menu_collection_schedule
       (collection_id, is_active, rule_type, day_of_week, specific_date);

CREATE TRIGGER menu_collection_schedule_updated_at
    BEFORE UPDATE ON public.menu_collection_schedule
    FOR EACH ROW
    EXECUTE FUNCTION public.menu_set_updated_at();

-- -------------------------------------------------------------------------
-- Collection-specific menu presentation
-- -------------------------------------------------------------------------
-- menu_item.category_id remains the item's canonical/default category for
-- backward compatibility.
--
-- menu_collection_category lets each collection choose which categories it
-- exposes and their ordering.
--
-- menu_collection_item.collection_category_id lets the same menu item appear
-- under a different category in a different collection.
--
-- menu_collection_item.price_override_minor lets a collection override the
-- item's normal/base price without duplicating the menu item.

CREATE TABLE public.menu_collection_category (
    id uuid NOT NULL,
    collection_id uuid NOT NULL,
    category_id uuid NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,

    CONSTRAINT menu_collection_category_pkey PRIMARY KEY (id),
    CONSTRAINT menu_collection_category_unique UNIQUE (collection_id, category_id),
    CONSTRAINT menu_collection_category_display_order_check CHECK (display_order >= 0),
    CONSTRAINT menu_collection_category_version_check CHECK (version >= 0),

    CONSTRAINT menu_collection_category_collection_id_fkey
        FOREIGN KEY (collection_id)
        REFERENCES public.menu_collection(id)
        ON DELETE CASCADE,

    CONSTRAINT menu_collection_category_category_id_fkey
        FOREIGN KEY (category_id)
        REFERENCES public.menu_category(id)
        ON DELETE RESTRICT
);

ALTER TABLE public.menu_collection_item
    ADD COLUMN collection_category_id uuid,
    ADD COLUMN price_override_minor bigint;

ALTER TABLE public.menu_collection_item
    ADD CONSTRAINT menu_collection_item_collection_category_id_fkey
        FOREIGN KEY (collection_category_id)
        REFERENCES public.menu_collection_category(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT menu_collection_item_price_override_minor_check
        CHECK (price_override_minor IS NULL OR price_override_minor >= 0);

CREATE INDEX menu_collection_category_order
    ON public.menu_collection_category (collection_id, display_order, id);

CREATE INDEX menu_collection_item_category_order
    ON public.menu_collection_item
       (collection_id, collection_category_id, display_order, menu_item_id);

CREATE TRIGGER menu_collection_category_updated_at
    BEFORE UPDATE ON public.menu_collection_category
    FOR EACH ROW
    EXECUTE FUNCTION public.menu_set_updated_at();

CREATE TABLE public.menu_option_group (
    id uuid NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    selection_type character varying(20) DEFAULT 'SINGLE' NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,

    CONSTRAINT menu_option_group_pkey PRIMARY KEY (id),
    CONSTRAINT menu_option_group_code_key UNIQUE (code),
    CONSTRAINT menu_option_group_code_check
        CHECK ((code)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text),
    CONSTRAINT menu_option_group_name_check
        CHECK (btrim((name)::text) <> ''::text),
    CONSTRAINT menu_option_group_selection_type_check
        CHECK ((selection_type)::text = ANY (
            ARRAY['SINGLE'::character varying, 'MULTIPLE'::character varying]::text[]
        )),
    CONSTRAINT menu_option_group_version_check CHECK (version >= 0)
);

CREATE TABLE public.menu_option (
    id uuid NOT NULL,
    option_group_id uuid NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    price_delta_minor bigint DEFAULT 0 NOT NULL,
    currency character varying(3) DEFAULT 'AUD' NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,

    CONSTRAINT menu_option_pkey PRIMARY KEY (id),
    CONSTRAINT menu_option_group_code_unique UNIQUE (option_group_id, code),
    CONSTRAINT menu_option_group_name_unique UNIQUE (option_group_id, name),
    CONSTRAINT menu_option_code_check
        CHECK ((code)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text),
    CONSTRAINT menu_option_name_check
        CHECK (btrim((name)::text) <> ''::text),
    CONSTRAINT menu_option_price_delta_minor_check CHECK (price_delta_minor >= 0),
    CONSTRAINT menu_option_currency_check CHECK ((currency)::text = 'AUD'::text),
    CONSTRAINT menu_option_display_order_check CHECK (display_order >= 0),
    CONSTRAINT menu_option_version_check CHECK (version >= 0),

    CONSTRAINT menu_option_group_id_fkey
        FOREIGN KEY (option_group_id)
        REFERENCES public.menu_option_group(id)
        ON DELETE RESTRICT
);

CREATE TABLE public.menu_item_option_group (
    menu_item_id uuid NOT NULL,
    option_group_id uuid NOT NULL,
    min_selections integer DEFAULT 0 NOT NULL,
    max_selections integer DEFAULT 1 NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,

    CONSTRAINT menu_item_option_group_pkey
        PRIMARY KEY (menu_item_id, option_group_id),
    CONSTRAINT menu_item_option_group_selection_check
        CHECK (
            min_selections >= 0
            AND max_selections >= 1
            AND max_selections >= min_selections
        ),
    CONSTRAINT menu_item_option_group_display_order_check CHECK (display_order >= 0),
    CONSTRAINT menu_item_option_group_version_check CHECK (version >= 0),

    CONSTRAINT menu_item_option_group_menu_item_id_fkey
        FOREIGN KEY (menu_item_id)
        REFERENCES public.menu_item(id)
        ON DELETE RESTRICT,

    CONSTRAINT menu_item_option_group_option_group_id_fkey
        FOREIGN KEY (option_group_id)
        REFERENCES public.menu_option_group(id)
        ON DELETE RESTRICT
);

-- Snapshot of selected options on an order line.
-- restaurant_order_item.unit_price_minor should contain the final per-unit
-- price including all selected option price deltas.
CREATE TABLE public.restaurant_order_item_option (
    id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    option_id uuid NOT NULL,
    option_group_name character varying(100) NOT NULL,
    option_name character varying(100) NOT NULL,
    price_delta_minor bigint DEFAULT 0 NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT restaurant_order_item_option_pkey PRIMARY KEY (id),
    CONSTRAINT restaurant_order_item_option_unique
        UNIQUE (order_item_id, option_id),
    CONSTRAINT restaurant_order_item_option_group_name_check
        CHECK (btrim((option_group_name)::text) <> ''::text),
    CONSTRAINT restaurant_order_item_option_name_check
        CHECK (btrim((option_name)::text) <> ''::text),
    CONSTRAINT restaurant_order_item_option_price_delta_minor_check
        CHECK (price_delta_minor >= 0),
    CONSTRAINT restaurant_order_item_option_quantity_check
        CHECK (quantity >= 1 AND quantity <= 20),

    CONSTRAINT restaurant_order_item_option_order_item_id_fkey
        FOREIGN KEY (order_item_id)
        REFERENCES public.restaurant_order_item(id)
        ON DELETE RESTRICT,

    CONSTRAINT restaurant_order_item_option_option_id_fkey
        FOREIGN KEY (option_id)
        REFERENCES public.menu_option(id)
        ON DELETE RESTRICT
);

CREATE INDEX menu_option_order
    ON public.menu_option (option_group_id, display_order, id);

CREATE INDEX menu_item_option_group_order
    ON public.menu_item_option_group (menu_item_id, display_order, option_group_id);

CREATE INDEX menu_item_option_group_reverse
    ON public.menu_item_option_group (option_group_id);

CREATE INDEX restaurant_order_item_option_order
    ON public.restaurant_order_item_option (order_item_id, id);

CREATE TRIGGER menu_option_group_updated_at
    BEFORE UPDATE ON public.menu_option_group
    FOR EACH ROW
    EXECUTE FUNCTION public.menu_set_updated_at();

CREATE TRIGGER menu_option_updated_at
    BEFORE UPDATE ON public.menu_option
    FOR EACH ROW
    EXECUTE FUNCTION public.menu_set_updated_at();

CREATE TRIGGER menu_item_option_group_updated_at
    BEFORE UPDATE ON public.menu_item_option_group
    FOR EACH ROW
    EXECUTE FUNCTION public.menu_set_updated_at();
