-- Payment/provider receipts cannot pay two different orders. Existing duplicates
-- require operational review rather than silently discarding financial records.
CREATE UNIQUE INDEX order_payment_confirmation_reference_unique
 ON order_payment(method,confirmation_reference) WHERE confirmation_reference IS NOT NULL;
