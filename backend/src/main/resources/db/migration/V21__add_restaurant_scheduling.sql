-- Restaurant-owned business timezone and availability. No opening hours or
-- closure dates are assumed: the restaurant is closed until hours are configured.
CREATE TABLE restaurant_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    timezone VARCHAR(64) NOT NULL CHECK (btrim(timezone) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

INSERT INTO restaurant_settings (id, timezone) VALUES (1, 'Australia/Melbourne');

CREATE TABLE restaurant_opening_hours (
    id UUID PRIMARY KEY,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens_at TIME NOT NULL CHECK (opens_at < TIME '24:00:00'),
    closes_at TIME NOT NULL CHECK (closes_at < TIME '24:00:00'),
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (opens_at <> closes_at)
);

CREATE INDEX restaurant_opening_hours_active_day
    ON restaurant_opening_hours (day_of_week, display_order) WHERE is_active;

CREATE TABLE restaurant_closed_date (
    id UUID PRIMARY KEY,
    closed_date DATE NOT NULL UNIQUE,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

-- Reuse the existing timestamp trigger function.
CREATE TRIGGER restaurant_settings_updated_at BEFORE UPDATE ON restaurant_settings
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();
CREATE TRIGGER restaurant_opening_hours_updated_at BEFORE UPDATE ON restaurant_opening_hours
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();
CREATE TRIGGER restaurant_closed_date_updated_at BEFORE UPDATE ON restaurant_closed_date
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();
