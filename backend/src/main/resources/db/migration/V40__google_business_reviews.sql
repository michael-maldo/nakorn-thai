CREATE TABLE google_review_connection (
    restaurant_id smallint PRIMARY KEY REFERENCES restaurant_settings(id),
    refresh_credential text,
    location_resource text,
    location_title text,
    location_address text,
    maps_url text,
    metadata_loaded_at timestamptz,
    authorization_expired boolean NOT NULL DEFAULT false,
    generation uuid NOT NULL DEFAULT gen_random_uuid(),
    last_attempt timestamptz,
    last_success timestamptz,
    sync_error text,
    total_reviews integer CHECK (total_reviews >= 0),
    overall_rating double precision CHECK (overall_rating BETWEEN 0 AND 5),
    homepage_enabled boolean NOT NULL DEFAULT false,
    homepage_limit smallint NOT NULL DEFAULT 6 CHECK (homepage_limit IN (3, 6, 9)),
    homepage_order text NOT NULL DEFAULT 'NEWEST_SELECTED' CHECK (homepage_order IN ('NEWEST_SELECTED','ROTATE')),
    settings_updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO google_review_connection(restaurant_id) VALUES (1);

CREATE TABLE google_review_oauth (
    state_hash text PRIMARY KEY,
    restaurant_id smallint NOT NULL REFERENCES google_review_connection(restaurant_id),
    staff_session_id uuid NOT NULL REFERENCES staff_session(id) ON DELETE CASCADE,
    generation uuid NOT NULL,
    verifier text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed boolean NOT NULL DEFAULT false,
    pending_credential text,
    error text
);
CREATE INDEX google_review_oauth_expiration ON google_review_oauth(expires_at);

CREATE TABLE google_review_selection (
    restaurant_id smallint NOT NULL REFERENCES google_review_connection(restaurant_id),
    location_id text NOT NULL,
    review_key text NOT NULL,
    featured boolean NOT NULL,
    updated_by text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (restaurant_id, location_id, review_key)
);
