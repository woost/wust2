CREATE TABLE usedfeature (
    userid uuid NOT NULL REFERENCES node(id) ON DELETE CASCADE,
    feature jsonb NOT NULL,
    timestamp timestamp without time zone NOT NULL,

    unique(userid, feature)
);
