-- Existing reservation rows and verification flags are deliberately unchanged.
-- V29 already enforces UNIQUE(reservation_id,type,channel).
ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_type_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_type_check
 CHECK (type IN ('RESERVATION_RECEIVED','RESERVATION_CONFIRMED','RESERVATION_CANCELLED','RESERVATION_DECLINED',
 'ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED'));
ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_owner_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_owner_check
 CHECK ((reservation_id IS NOT NULL AND order_id IS NULL AND type IN
 ('RESERVATION_RECEIVED','RESERVATION_CONFIRMED','RESERVATION_CANCELLED','RESERVATION_DECLINED'))
 OR (reservation_id IS NULL AND order_id IS NOT NULL AND type IN
 ('ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED')));
