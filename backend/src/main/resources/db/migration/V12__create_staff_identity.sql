CREATE TABLE staff_user (
 id uuid PRIMARY KEY,
 username varchar(50) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,49}$'),
 password_hash varchar(100) NOT NULL,
 role varchar(20) NOT NULL CHECK (role IN ('ADMIN','FOH','BOH')),
 enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0
);
CREATE TABLE staff_session (
 id uuid PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES staff_user(id) ON DELETE RESTRICT,
 refresh_hash varchar(64) NOT NULL,
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz
);
CREATE INDEX staff_session_user ON staff_session(user_id);
