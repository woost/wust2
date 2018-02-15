CREATE TABLE usergroupInvite (
    groupId integer PRIMARY KEY REFERENCES usergroup ON DELETE CASCADE,
    token text NOT NULL,
    UNIQUE(token)
);

CREATE INDEX on usergroupInvite(token);
