ALTER TABLE post
    RENAME TO rawpost;

AlTER TABLE rawpost
    ADD COLUMN isdeleted boolean NOT NULL DEFAULT false;

CREATE INDEX on rawpost(isdeleted);

CREATE VIEW post AS SELECT id,title FROM rawpost WHERE isdeleted = false;
