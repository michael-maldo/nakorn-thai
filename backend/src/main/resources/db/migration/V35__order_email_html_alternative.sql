-- Optional, captured HTML alternative. Existing jobs/reservation emails stay plain text.
ALTER TABLE notification_delivery ADD COLUMN html_body text;
