-- Additive only: retain historical capture/payment/order data.
ALTER TABLE restaurant_order ADD COLUMN refund_status varchar(20) NOT NULL DEFAULT 'NONE'
 CHECK (refund_status IN ('NONE','REQUIRED','PENDING','COMPLETED','MANUAL_REVIEW'));
ALTER TABLE restaurant_order ADD COLUMN payment_review_required boolean NOT NULL DEFAULT false;
UPDATE restaurant_order SET refund_status='REQUIRED' WHERE status='CANCELLED' AND paid_at IS NOT NULL;

ALTER TABLE order_payment ADD COLUMN provider_identity varchar(64);
ALTER TABLE order_payment ADD COLUMN financial_status varchar(24) NOT NULL DEFAULT 'UNCONFIRMED'
 CHECK (financial_status IN ('UNCONFIRMED','CAPTURED','PARTIALLY_REFUNDED','REFUNDED','MANUAL_REVIEW'));
ALTER TABLE order_payment ADD COLUMN reconcile_after timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE order_payment ADD COLUMN reconcile_failures integer NOT NULL DEFAULT 0 CHECK(reconcile_failures>=0);
ALTER TABLE order_payment ADD COLUMN reconcile_attempts integer NOT NULL DEFAULT 0 CHECK(reconcile_attempts>=0);
ALTER TABLE order_payment ADD COLUMN review_reason varchar(80);
ALTER TABLE order_payment ADD COLUMN lease_id uuid;
ALTER TABLE order_payment ADD COLUMN lease_until timestamptz;
ALTER TABLE order_payment ADD COLUMN capture_attempted_at timestamptz;
UPDATE order_payment SET financial_status='CAPTURED' WHERE method='PAYPAL' AND status='PAID';
CREATE INDEX order_payment_reconciliation_due ON order_payment(reconcile_after) WHERE method='PAYPAL' AND review_reason IS NULL;
CREATE INDEX restaurant_order_refund_review ON restaurant_order(updated_at) WHERE refund_status IN ('REQUIRED','PENDING','MANUAL_REVIEW');

CREATE TABLE paypal_webhook_event (
 event_id varchar(100) PRIMARY KEY, event_type varchar(80) NOT NULL, resource_id varchar(100) NOT NULL,
 provider_identity varchar(64) NOT NULL, received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 processed_at timestamptz, attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
 next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, last_error varchar(80),
 lease_id uuid, lease_until timestamptz
);
CREATE INDEX paypal_webhook_due ON paypal_webhook_event(next_attempt_at) WHERE processed_at IS NULL;
CREATE TABLE paypal_refund (
 provider_refund_id varchar(100) PRIMARY KEY, order_id uuid NOT NULL REFERENCES restaurant_order(id) ON DELETE CASCADE,
 capture_id varchar(100) NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor>0),
 status varchar(20) NOT NULL CHECK(status IN ('PENDING','COMPLETED','FAILED','CANCELLED')),
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX paypal_refund_order ON paypal_refund(order_id);
CREATE TABLE payment_audit (
 id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES restaurant_order(id) ON DELETE CASCADE,
 occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, action varchar(80) NOT NULL,
 actor varchar(100) NOT NULL, detail varchar(100) NOT NULL
);
CREATE INDEX payment_audit_order ON payment_audit(order_id,occurred_at);
