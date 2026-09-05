-- Pickup orders snapshot menu details and prices; menu edits never rewrite orders.
CREATE TABLE restaurant_order (
 id uuid PRIMARY KEY,
 tracking_hash varchar(64) NOT NULL,
 request_hash varchar(64) NOT NULL,
 customer_name varchar(100) NOT NULL,
 phone varchar(30) NOT NULL,
 notes varchar(1000) NOT NULL DEFAULT '',
 status varchar(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','ACCEPTED','PREPARING','READY','COMPLETED','CANCELLED')),
 total_minor bigint NOT NULL CHECK (total_minor >= 0),
 currency varchar(3) NOT NULL DEFAULT 'AUD' CHECK (currency='AUD'),
 fulfilment varchar(20) NOT NULL DEFAULT 'PICKUP' CHECK (fulfilment='PICKUP'),
 payment_method varchar(30) NOT NULL DEFAULT 'PAY_AT_RESTAURANT' CHECK (payment_method='PAY_AT_RESTAURANT'),
 paid_at timestamptz,
 estimated_ready_at timestamptz,
 cancellation_reason varchar(500),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0,
 CHECK (status <> 'COMPLETED' OR paid_at IS NOT NULL)
);
CREATE INDEX restaurant_order_queue ON restaurant_order(status, created_at);
CREATE TABLE restaurant_order_item (
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES restaurant_order(id) ON DELETE RESTRICT,
 variation_id uuid NOT NULL REFERENCES menu_item_variation(id) ON DELETE RESTRICT,
 dish_name varchar(150) NOT NULL,
 variation_name varchar(100) NOT NULL,
 quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 20),
 unit_price_minor bigint NOT NULL CHECK (unit_price_minor >= 0)
);
CREATE INDEX restaurant_order_item_order ON restaurant_order_item(order_id);
CREATE TABLE restaurant_order_event (
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES restaurant_order(id) ON DELETE RESTRICT,
 status varchar(20) NOT NULL,
 actor varchar(100) NOT NULL,
 created_at timestamptz NOT NULL
);
CREATE INDEX restaurant_order_event_order ON restaurant_order_event(order_id);
