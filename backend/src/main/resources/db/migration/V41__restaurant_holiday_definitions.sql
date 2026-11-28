CREATE TABLE restaurant_holiday_definition (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (name <> '' AND name = btrim(name)),
    start_date DATE NOT NULL CHECK (start_date >= DATE '0001-01-01'),
    end_date DATE NOT NULL CHECK (end_date <= DATE '9999-12-31'),
    recurrence VARCHAR(8) NOT NULL CHECK (recurrence IN ('ONE_TIME', 'ANNUAL')),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (end_date >= start_date),
    CHECK (recurrence = 'ONE_TIME' OR
        EXTRACT(YEAR FROM end_date) = EXTRACT(YEAR FROM start_date) OR
        (EXTRACT(YEAR FROM end_date) = EXTRACT(YEAR FROM start_date) + 1 AND
         (EXTRACT(MONTH FROM end_date), EXTRACT(DAY FROM end_date)) <
         (EXTRACT(MONTH FROM start_date), EXTRACT(DAY FROM start_date))))
);

-- Notes, active status, and annual anchor years do not distinguish duplicates.
CREATE UNIQUE INDEX holiday_one_time_identity
    ON restaurant_holiday_definition (lower(name), start_date, end_date)
    WHERE recurrence = 'ONE_TIME';
CREATE UNIQUE INDEX holiday_annual_identity
    ON restaurant_holiday_definition (lower(name),
        EXTRACT(MONTH FROM start_date), EXTRACT(DAY FROM start_date),
        EXTRACT(MONTH FROM end_date), EXTRACT(DAY FROM end_date))
    WHERE recurrence = 'ANNUAL';

CREATE TRIGGER restaurant_holiday_definition_updated_at
    BEFORE UPDATE ON restaurant_holiday_definition
    FOR EACH ROW EXECUTE FUNCTION menu_set_updated_at();
