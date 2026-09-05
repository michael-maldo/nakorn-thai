CREATE TABLE function_enquiry (
 id uuid PRIMARY KEY,
 customer_name varchar(100) NOT NULL CHECK (btrim(customer_name) <> ''),
 email varchar(254) NOT NULL,
 phone varchar(30) NOT NULL,
 event_type varchar(80) NOT NULL,
 guest_count integer NOT NULL CHECK (guest_count BETWEEN 1 AND 1000),
 preferred_date date,
 preferred_time varchar(100) NOT NULL DEFAULT '',
 message varchar(2000) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','CONTACTED','CONFIRMED','DECLINED','CANCELLED','COMPLETED')),
 arranged_date date,
 staff_note varchar(2000) NOT NULL DEFAULT '',
 updated_by varchar(50),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0,
 CHECK (status NOT IN ('CONFIRMED','COMPLETED') OR arranged_date IS NOT NULL)
);
CREATE INDEX function_enquiry_queue_idx ON function_enquiry(status,created_at,id);
