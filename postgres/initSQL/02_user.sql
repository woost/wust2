CREATE TABLE user (
    id serial PRIMARY KEY,
    name text NOT NULL UNIQUE
);

CREATE TABLE password (
    id PRIMARY KEY REFERENCES atom ON DELETE CASCADE,
    digest text NOT NULL
);
