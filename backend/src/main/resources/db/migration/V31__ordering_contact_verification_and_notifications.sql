-- Preserve legacy orders without claiming their phone was verified.
ALTER TABLE restaurant_order ADD COLUMN phone_verified boolean NOT NULL DEFAULT false;
-- Keep reservation consumption FK and add a separate, mutually exclusive order FK.
ALTER TABLE contact_verification ADD COLUMN consumed_order_id uuid
 REFERENCES restaurant_order(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE contact_verification ADD CONSTRAINT contact_verification_single_consumer
 CHECK (num_nonnulls(consumed_by,consumed_order_id)<=1);
ALTER TABLE notification_delivery ALTER COLUMN reservation_id DROP NOT NULL;
ALTER TABLE notification_delivery ADD COLUMN order_id uuid REFERENCES restaurant_order(id);
ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_type_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_type_check
 CHECK (type IN ('RESERVATION_CONFIRMED','ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED'));
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_owner_check
 CHECK ((reservation_id IS NOT NULL AND order_id IS NULL AND type='RESERVATION_CONFIRMED')
 OR (reservation_id IS NULL AND order_id IS NOT NULL AND type IN ('ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED')));
CREATE UNIQUE INDEX notification_delivery_order_event_unique ON notification_delivery(order_id,type,channel)
 WHERE order_id IS NOT NULL;
CREATE INDEX contact_verification_consumed_order_idx ON contact_verification(consumed_order_id)
 WHERE consumed_order_id IS NOT NULL;
