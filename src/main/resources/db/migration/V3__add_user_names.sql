-- Description: Add first_name and optional last_name to users table
ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN last_name VARCHAR(100);

ALTER TABLE users
    ALTER COLUMN first_name DROP DEFAULT;
