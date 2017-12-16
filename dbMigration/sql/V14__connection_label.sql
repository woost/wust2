CREATE TABLE label (
    id SERIAL PRIMARY KEY,
    name text NOT NULL
);

INSERT INTO label (name) VALUES ('related'), ('parent');

ALTER TABLE connection
    drop constraint connection_sourceid_fkey,
    drop constraint connection_targetid_fkey,
    drop constraint connection_pkey,
    drop constraint selfloop;

ALTER TABLE connection RENAME TO rawconnection;

ALTER TABLE rawconnection
    ADD COLUMN label INTEGER NOT NULL REFERENCES label (id) ON DELETE RESTRICT DEFAULT 1,
    ADD constraint connection_sourceid_fkey foreign key (sourceId) references rawpost (id) on delete cascade,
    ADD constraint connection_targetid_fkey foreign key (targetId) references rawpost (id) on delete cascade,
    ADD constraint selfloop check (sourceId <> targetId),
    ADD constraint connection_pkey primary key (sourceId, label, targetId);

INSERT INTO rawconnection (select childid as sourceid, parentid as targetid, 2 as label from containment);

DROP TABLE containment;

ALTER TABLE rawconnection
    ALTER COLUMN label DROP DEFAULT;

ALTER TABLE label
    ADD CONSTRAINT unique_label_name UNIQUE (name);

CREATE INDEX lname ON label USING btree(name);
