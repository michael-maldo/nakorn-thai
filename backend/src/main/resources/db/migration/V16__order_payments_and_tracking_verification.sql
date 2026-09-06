ALTER TABLE restaurant_order DROP CONSTRAINT restaurant_order_payment_method_check;
ALTER TABLE restaurant_order ADD CONSTRAINT restaurant_order_payment_method_check CHECK (payment_method IN ('PAY_AT_RESTAURANT','PAYPAL','PAYID'));
ALTER TABLE restaurant_order ADD COLUMN email varchar(254);
CREATE TABLE order_payment (
 order_id uuid PRIMARY KEY REFERENCES restaurant_order(id),
 method varchar(30) NOT NULL CHECK (method IN ('PAYPAL','PAYID')),
 provider_order_id varchar(100) UNIQUE,
 approval_url text,
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID')),
 confirmation_reference varchar(150),
 confirmed_by varchar(100),
 updated_at timestamptz NOT NULL
);
CREATE TABLE order_verification (
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES restaurant_order(id),
 destination_hash varchar(64) NOT NULL,
 channel varchar(10) NOT NULL CHECK (channel IN ('sms','email')),
 provider_sid varchar(100),
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 attempts integer NOT NULL DEFAULT 0,
 consumed boolean NOT NULL DEFAULT false
);
CREATE INDEX order_verification_rate ON order_verification(destination_hash,created_at);
CREATE INDEX order_verification_order ON order_verification(order_id,created_at);
CREATE TABLE order_tracking_grant (
 token_hash varchar(64) PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES restaurant_order(id),
 expires_at timestamptz NOT NULL
);
