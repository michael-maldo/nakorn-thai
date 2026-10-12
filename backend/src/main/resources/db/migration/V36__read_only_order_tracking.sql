-- Separate from original receipt tokens and 24-hour OTP recovery grants.
CREATE TABLE order_view_token (
 order_id uuid PRIMARY KEY REFERENCES restaurant_order(id) ON DELETE CASCADE,
 token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
 encrypted_token text NOT NULL,
 issued_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz,
 CONSTRAINT order_view_token_seven_days CHECK (expires_at = issued_at + interval '168 hours'),
 CONSTRAINT order_view_token_revocation CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);
CREATE INDEX order_view_token_expiry_idx ON order_view_token(expires_at);
