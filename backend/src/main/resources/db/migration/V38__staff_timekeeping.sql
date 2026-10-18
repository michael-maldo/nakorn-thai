-- btree_gist supplies UUID equality for the per-employee range exclusion.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE staff_attendance (
    id uuid PRIMARY KEY,
    staff_user_id uuid NOT NULL REFERENCES staff_user(id) ON DELETE RESTRICT,
    clock_in timestamptz NOT NULL,
    clock_out timestamptz,
    created_by uuid NOT NULL REFERENCES staff_user(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (isfinite(clock_in) AND (clock_out IS NULL OR (isfinite(clock_out) AND clock_out > clock_in))),
    EXCLUDE USING gist (staff_user_id WITH =, tstzrange(clock_in, clock_out, '[)') WITH &&)
);
CREATE INDEX staff_attendance_staff_start ON staff_attendance(staff_user_id, clock_in, id);
CREATE INDEX staff_attendance_start ON staff_attendance(clock_in, id);
CREATE UNIQUE INDEX staff_attendance_one_open ON staff_attendance(staff_user_id) WHERE clock_out IS NULL;
CREATE TABLE staff_attendance_audit (
    id uuid PRIMARY KEY,
    attendance_id uuid NOT NULL REFERENCES staff_attendance(id) ON DELETE RESTRICT,
    action varchar(20) NOT NULL CHECK (action IN ('CLOCK_IN','CLOCK_OUT','MANUAL_ENTRY','CORRECTION')),
    actor_id uuid NOT NULL REFERENCES staff_user(id) ON DELETE RESTRICT,
    occurred_at timestamptz NOT NULL,
    previous_values jsonb,
    new_values jsonb NOT NULL,
    reason varchar(1000),
    CHECK (action NOT IN ('MANUAL_ENTRY','CORRECTION') OR (reason IS NOT NULL AND reason ~ '[^[:space:]]')),
    CHECK (jsonb_typeof(new_values) = 'object'),
    CHECK (previous_values IS NULL OR jsonb_typeof(previous_values) = 'object')
);
CREATE INDEX staff_attendance_audit_entry ON staff_attendance_audit(attendance_id, occurred_at, id);
CREATE FUNCTION staff_attendance_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Attendance audit is append-only' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER staff_attendance_audit_immutable
BEFORE UPDATE OR DELETE OR TRUNCATE ON staff_attendance_audit
FOR EACH STATEMENT EXECUTE FUNCTION staff_attendance_audit_immutable();
