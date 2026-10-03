ALTER TABLE reservation ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE reservation ADD COLUMN email varchar(254), ADD COLUMN phone_verified boolean NOT NULL DEFAULT false, ADD COLUMN email_verified boolean NOT NULL DEFAULT false;
CREATE TABLE contact_verification (
 id uuid PRIMARY KEY, channel varchar(10) NOT NULL CHECK(channel IN ('SMS','EMAIL')),
 destination_hash varchar(64) NOT NULL, provider_reference varchar(100),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 verified_at timestamptz, consumed_by uuid REFERENCES reservation(id) DEFERRABLE INITIALLY DEFERRED,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5)
);
CREATE INDEX contact_verification_rate_idx ON contact_verification(destination_hash,created_at);
CREATE TABLE notification_delivery (
 id uuid PRIMARY KEY, reservation_id uuid NOT NULL REFERENCES reservation(id),
 type varchar(40) NOT NULL CHECK(type='RESERVATION_CONFIRMED'),
 channel varchar(10) NOT NULL CHECK(channel IN ('SMS','EMAIL')), recipient varchar(254) NOT NULL,
 subject varchar(200) NOT NULL, body text NOT NULL,
 status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','FAILED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 created_at timestamptz NOT NULL, next_attempt_at timestamptz NOT NULL, sent_at timestamptz, last_error varchar(200),
 UNIQUE(reservation_id,type,channel)
);
CREATE INDEX notification_delivery_due_idx ON notification_delivery(next_attempt_at) WHERE status<>'SENT' AND attempts<5;
