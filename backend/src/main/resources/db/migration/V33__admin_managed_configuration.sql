-- No environment secrets are imported. Existing settings/data are unchanged.
ALTER TABLE restaurant_settings ADD COLUMN operational_configuration jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE TABLE integration_configuration (
 category varchar(20) PRIMARY KEY CHECK(category IN ('PAYPAL','PAYID','TWILIO','SMTP')),
 fields jsonb NOT NULL DEFAULT '{}'::jsonb, secrets jsonb NOT NULL DEFAULT '{}'::jsonb,
 version bigint NOT NULL DEFAULT 0, validation_status varchar(20) NOT NULL DEFAULT 'NOT_TESTED' CHECK(validation_status IN ('NOT_TESTED','VALID','INVALID')),
 tested_at timestamptz, updated_at timestamptz, updated_by varchar(100)
);
INSERT INTO integration_configuration(category) VALUES ('PAYPAL'),('PAYID'),('TWILIO'),('SMTP');
CREATE TABLE configuration_audit (
 id uuid PRIMARY KEY, occurred_at timestamptz NOT NULL, actor varchar(100) NOT NULL,
 category varchar(20) NOT NULL, action varchar(60) NOT NULL, changed_fields jsonb NOT NULL
);
CREATE INDEX configuration_audit_time_idx ON configuration_audit(occurred_at DESC);
