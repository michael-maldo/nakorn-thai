-- Requested starting login: admin / admin, with full ADMIN access.
-- Store only bcrypt. Never reset an existing account, role, status or password.
-- No FOH/BOH accounts or staff sessions are copied from development.
INSERT INTO staff_user (id, username, password_hash, role, enabled, created_at, updated_at)
VALUES ('f82b9c75-f5cf-5fce-91aa-e6cbed8c1698', 'admin',
        '$2y$12$b8y83hB3obX0MgmMBynhQO8EfnzrkmYzQsChf87nH1zP173TZvMyO', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
