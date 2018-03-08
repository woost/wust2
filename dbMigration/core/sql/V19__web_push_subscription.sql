CREATE TABLE webpushsubscription (
    id serial PRIMARY KEY,
    userid CHARACTER VARYING(36) NOT NULL REFERENCES "user" ON DELETE CASCADE,
    endpointurl text NOT NULL,
    p256dh text NOT NULL,
    auth text NOT NULL
);
