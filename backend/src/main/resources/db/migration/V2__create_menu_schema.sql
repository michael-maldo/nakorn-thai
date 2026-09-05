-- Menu schema v1: twelve tables, PostgreSQL. UUIDs are supplied by clients.
-- Flyway owns DDL. Application writes must maintain version/review rules.
-- The database itself and login role must exist before Spring Boot starts.

CREATE FUNCTION menu_set_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TABLE menu_category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    slug VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description TEXT CHECK (btrim(description) <> ''),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_category_updated_at
    BEFORE UPDATE ON menu_category
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES menu_category(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
    slug VARCHAR(180) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    is_available BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    allergen_review_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REVIEWED' CHECK (allergen_review_status IN ('NOT_REVIEWED', 'REVIEWED', 'NEEDS_REVIEW')),
    allergen_reviewed_at TIMESTAMPTZ,
    CHECK (allergen_review_status <> 'REVIEWED' OR allergen_reviewed_at IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_updated_at
    BEFORE UPDATE ON menu_item
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_variation (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    sku VARCHAR(80) UNIQUE CHECK (btrim(sku) <> '' AND sku = upper(btrim(sku))),
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'AUD' CHECK (currency = 'AUD'),
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_available BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    CHECK (NOT is_default OR is_active),
    allergen_review_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REVIEWED' CHECK (allergen_review_status IN ('NOT_REVIEWED', 'REVIEWED', 'NEEDS_REVIEW')),
    allergen_reviewed_at TIMESTAMPTZ,
    CHECK (allergen_review_status <> 'REVIEWED' OR allergen_reviewed_at IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_variation_updated_at
    BEFORE UPDATE ON menu_item_variation
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_image (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE RESTRICT,
    storage_key TEXT NOT NULL CHECK (btrim(storage_key) <> ''),
    alt_text VARCHAR(255) NOT NULL CHECK (btrim(alt_text) <> ''),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    UNIQUE (menu_item_id, storage_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_image_updated_at
    BEFORE UPDATE ON menu_item_image
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_collection (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
    slug VARCHAR(180) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description TEXT CHECK (btrim(description) <> ''),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_collection_updated_at
    BEFORE UPDATE ON menu_collection
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_collection_item (
    collection_id UUID NOT NULL REFERENCES menu_collection(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE RESTRICT,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    PRIMARY KEY (collection_id, menu_item_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_collection_item_updated_at
    BEFORE UPDATE ON menu_collection_item
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE dietary_tag (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT NOT NULL CHECK (btrim(description) <> ''),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER dietary_tag_updated_at
    BEFORE UPDATE ON dietary_tag
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE allergen (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT CHECK (btrim(description) <> ''),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER allergen_updated_at
    BEFORE UPDATE ON allergen
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_dietary_tag (
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE RESTRICT,
    dietary_tag_id UUID NOT NULL REFERENCES dietary_tag(id) ON DELETE RESTRICT,
    notes TEXT CHECK (btrim(notes) <> ''),
    verified_at TIMESTAMPTZ,
    PRIMARY KEY (menu_item_id, dietary_tag_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_dietary_tag_updated_at
    BEFORE UPDATE ON menu_item_dietary_tag
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_allergen (
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE RESTRICT,
    allergen_id UUID NOT NULL REFERENCES allergen(id) ON DELETE RESTRICT,
    declaration VARCHAR(20) NOT NULL CHECK (declaration IN ('CONTAINS', 'MAY_CONTAIN')),
    notes TEXT CHECK (btrim(notes) <> ''),
    verified_at TIMESTAMPTZ,
    PRIMARY KEY (menu_item_id, allergen_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_allergen_updated_at
    BEFORE UPDATE ON menu_item_allergen
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_variation_dietary_tag (
    variation_id UUID NOT NULL REFERENCES menu_item_variation(id) ON DELETE RESTRICT,
    dietary_tag_id UUID NOT NULL REFERENCES dietary_tag(id) ON DELETE RESTRICT,
    notes TEXT CHECK (btrim(notes) <> ''),
    verified_at TIMESTAMPTZ,
    PRIMARY KEY (variation_id, dietary_tag_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_variation_dietary_tag_updated_at
    BEFORE UPDATE ON menu_item_variation_dietary_tag
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE TABLE menu_item_variation_allergen (
    variation_id UUID NOT NULL REFERENCES menu_item_variation(id) ON DELETE RESTRICT,
    allergen_id UUID NOT NULL REFERENCES allergen(id) ON DELETE RESTRICT,
    declaration VARCHAR(20) NOT NULL CHECK (declaration IN ('CONTAINS', 'MAY_CONTAIN')),
    notes TEXT CHECK (btrim(notes) <> ''),
    verified_at TIMESTAMPTZ,
    PRIMARY KEY (variation_id, allergen_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TRIGGER menu_item_variation_allergen_updated_at
    BEFORE UPDATE ON menu_item_variation_allergen
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();

CREATE INDEX menu_item_category_order ON menu_item (category_id, status, display_order, id);
CREATE INDEX menu_variation_order ON menu_item_variation (menu_item_id, display_order, id);
CREATE INDEX menu_image_order ON menu_item_image (menu_item_id, display_order, id);
CREATE INDEX menu_collection_order ON menu_collection (status, display_order, id);
CREATE INDEX menu_membership_order ON menu_collection_item (collection_id, display_order, menu_item_id);
CREATE INDEX menu_membership_item ON menu_collection_item (menu_item_id);
CREATE INDEX menu_item_dietary_tag_reverse ON menu_item_dietary_tag (dietary_tag_id);
CREATE INDEX menu_item_allergen_reverse ON menu_item_allergen (allergen_id);
CREATE INDEX menu_item_variation_dietary_tag_reverse ON menu_item_variation_dietary_tag (dietary_tag_id);
CREATE INDEX menu_item_variation_allergen_reverse ON menu_item_variation_allergen (allergen_id);
CREATE UNIQUE INDEX menu_variation_name ON menu_item_variation (menu_item_id, lower(name));
CREATE UNIQUE INDEX menu_variation_default ON menu_item_variation (menu_item_id) WHERE is_default;
CREATE UNIQUE INDEX menu_image_primary ON menu_item_image (menu_item_id) WHERE is_primary;
