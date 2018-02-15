DROP VIEW connection;
DROP VIEW containment;
DROP VIEW post;

CREATE TABLE post (id serial PRIMARY KEY, title text NOT NULL);
INSERT INTO post (id,title) SELECT id,title from _post;
SELECT pg_catalog.setval(pg_get_serial_sequence('post', 'id'), (SELECT MAX(id) FROM post)+1); -- fix post id serial

CREATE TABLE connection (
    sourceid integer NOT NULL REFERENCES post ON DELETE CASCADE,
    targetid integer NOT NULL REFERENCES post ON DELETE CASCADE,
    PRIMARY KEY (sourceid, targetid)
);

CREATE TABLE containment (
    parentid integer NOT NULL REFERENCES post ON DELETE CASCADE,
    childid integer NOT NULL REFERENCES post ON DELETE CASCADE,
    PRIMARY KEY (parentid, childid)
);

INSERT INTO connection SELECT i.sourceid, i.targetid FROM _connects c JOIN _incidence i ON c.id = i.id ON CONFLICT DO NOTHING;
INSERT INTO containment SELECT i.sourceid, i.targetid FROM _contains c JOIN _incidence i ON c.id = i.id ON CONFLICT DO NOTHING;

DROP TABLE _connects;
DROP TABLE _contains;
DROP TABLE _incidence;


ALTER TABLE ownership
    DROP CONSTRAINT "ownership_postid_fkey",
    ADD CONSTRAINT "ownership_postid_fkey" FOREIGN KEY (postid) REFERENCES post(id) ON DELETE CASCADE;

DROP TABLE _post;
DROP TABLE atom;
