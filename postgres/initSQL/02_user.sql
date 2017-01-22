CREATE TABLE "user" (
    id serial PRIMARY KEY,
    name text NOT NULL UNIQUE
);

CREATE TABLE password (
    id integer PRIMARY KEY REFERENCES "user" ON DELETE CASCADE,
    digest bytea NOT NULL
);
