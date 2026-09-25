-- Preserve existing ordering behaviour until staff explicitly pause it.
ALTER TABLE restaurant_settings
    ADD COLUMN ordering_paused boolean NOT NULL DEFAULT false,
    ADD COLUMN ordering_pause_message varchar(300);
