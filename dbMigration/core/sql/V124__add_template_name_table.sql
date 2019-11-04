CREATE TABLE nodetemplate (
    name text PRIMARY KEY,
    nodeid uuid NOT NULL REFERENCES node(id) ON DELETE CASCADE
);
