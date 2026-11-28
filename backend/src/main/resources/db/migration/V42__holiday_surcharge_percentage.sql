ALTER TABLE restaurant_holiday_definition
    ADD COLUMN surcharge_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00
    CHECK (surcharge_percentage >= 0.00 AND surcharge_percentage <= 100.00);
