ALTER TABLE rawpost
    drop constraint rawpost_userid_fkey;
ALTER TABLE password
    drop constraint password_id_fkey;
ALTER TABLE membership
    drop constraint usergroupmember_userid_fkey;

DROP VIEW post;

DROP SEQUENCE user_id_seq CASCADE;

ALTER TABLE "user"
    ALTER COLUMN id TYPE CHARACTER VARYING(36);
ALTER TABLE rawpost
    ALTER COLUMN author TYPE CHARACTER VARYING(36);
ALTER TABLE password
    ALTER COLUMN id TYPE CHARACTER VARYING(36);
ALTER TABLE membership
    ALTER COLUMN userId TYPE CHARACTER VARYING(36);

ALTER TABLE rawpost
    ADD constraint rawpost_userid_fkey foreign key (author) references "user" (id) on delete cascade;
ALTER TABLE password
    ADD constraint password_id_fkey foreign key (id) references "user" (id) on delete cascade;
ALTER TABLE membership
    ADD constraint usergroupmember_userid_fkey foreign key (userId) references "user" (id) on delete cascade;

CREATE OR REPLACE VIEW post AS
    SELECT id,content,author,created,modified
        FROM rawpost WHERE isdeleted = false;
