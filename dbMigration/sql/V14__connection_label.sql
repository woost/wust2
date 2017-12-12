CREATE TABLE label (
    id CHARACTER VARYING(36) PRIMARY KEY,
    name text NOT NULL
);

INSERT INTO label VALUES ('cjb3s6646000a040pxnbhvh1l', 'related');
INSERT INTO label VALUES ('cjb3rz3et0009040pfvwgpudt', 'parent');

ALTER TABLE connection
    ADD COLUMN label CHARACTER VARYING(36) NOT NULL REFERENCES label ON DELETE RESTRICT DEFAULT 'cjb3s6646000a040pxnbhvh1l',
    drop constraint connection_pkey,
    ADD constraint connection_pkey primary key (sourceId, label, targetId);


INSERT INTO connection (select childid as sourceid, parentid as targetid, 'cjb3rz3et0009040pfvwgpudt' as label from containment);

DROP TABLE containment;

ALTER TABLE connection
    ALTER COLUMN label DROP DEFAULT;
