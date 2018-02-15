CREATE TABLE usergroup (
    id serial PRIMARY KEY
);

-- userId is nullable in order to model public groups:
-- if the userId is null, every user is a member of this group.
CREATE TABLE usergroupMember (
    groupId integer NOT NULL REFERENCES usergroup ON DELETE CASCADE,
    userId integer REFERENCES "user" ON DELETE CASCADE,
    UNIQUE(groupId, userId)
);

CREATE INDEX on usergroupMember(userId);
CREATE INDEX on usergroupMember(groupId);

-- create public user group, will have id 1. this needs to correspond to the one used in the application
insert into usergroup(id) values(DEFAULT);
insert into usergroupMember(groupId, userId) values(1, NULL);

CREATE TABLE ownership (
    postId integer NOT NULL REFERENCES _post ON DELETE CASCADE,
    groupId integer NOT NULL REFERENCES usergroup ON DELETE CASCADE,
    UNIQUE(postId, groupId)
);

CREATE INDEX on ownership(postId);
CREATE INDEX on ownership(groupId);
