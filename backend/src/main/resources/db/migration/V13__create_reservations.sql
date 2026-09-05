CREATE TABLE reservation (
 id uuid PRIMARY KEY,
 customer_name varchar(100) NOT NULL,
 phone varchar(30) NOT NULL,
 party_size integer NOT NULL CHECK (party_size BETWEEN 1 AND 20),
 requested_at timestamp NOT NULL,
 notes varchar(1000) NOT NULL DEFAULT '',
 status varchar(20) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','CONFIRMED','DECLINED','CANCELLED','SEATED','NO_SHOW')),
 staff_note varchar(500) NOT NULL DEFAULT '',
 updated_by varchar(50),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0
);
CREATE INDEX reservation_requested_at_idx ON reservation(requested_at);
