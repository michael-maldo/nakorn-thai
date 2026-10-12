ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_type_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_type_check
 CHECK (type IN ('RESERVATION_RECEIVED','RESERVATION_CONFIRMED','RESERVATION_CANCELLED','RESERVATION_DECLINED',
 'STAFF_ORDER_RECEIVED','ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED'));
ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_owner_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_owner_check
 CHECK ((reservation_id IS NOT NULL AND order_id IS NULL AND type IN
 ('RESERVATION_RECEIVED','RESERVATION_CONFIRMED','RESERVATION_CANCELLED','RESERVATION_DECLINED'))
 OR (reservation_id IS NULL AND order_id IS NOT NULL AND type IN
 ('STAFF_ORDER_RECEIVED','ORDER_RECEIVED','ORDER_ACCEPTED','ORDER_READY','ORDER_CANCELLED')));

-- Staff purpose is email-only; the existing order/type/channel unique index
-- independently protects customer and staff jobs, including historical records.
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_staff_channel_check
 CHECK (type <> 'STAFF_ORDER_RECEIVED' OR channel = 'EMAIL');
