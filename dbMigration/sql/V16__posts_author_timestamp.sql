/** This migration adds author, creation and modification time to posts.
 * Previous posts are set to an created 'unknown' user.
 * Therefore, the rawpost table is modified and the post view re-created.
 */

CREATE FUNCTION now_utc() RETURNS TIMESTAMP AS $$
    SELECT NOW() AT TIME ZONE 'utc';
$$ language sql;

DROP VIEW post;

ALTER TABLE rawpost RENAME COLUMN title TO content;

ALTER TABLE rawpost
    ADD COLUMN author INTEGER,
    ADD COLUMN created TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now_utc(),
    ADD COLUMN modified TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now_utc();

WITH aid AS (
    INSERT INTO "user" (name) VALUES ('unknown') RETURNING id
) UPDATE rawpost
    SET author = (SELECT id FROM aid);

ALTER TABLE rawpost ADD CONSTRAINT rawpost_userid_fkey FOREIGN KEY (author) REFERENCES "user" (id);
ALTER TABLE rawpost ALTER COLUMN author SET NOT NULL;

CREATE OR REPLACE VIEW post AS
    SELECT id,content,author,created,modified
        FROM rawpost WHERE isdeleted = false;
