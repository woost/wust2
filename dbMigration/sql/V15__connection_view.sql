/** This migration creates a view for connections queries.
 * The goal is to eliminate consequtive joins of labels for connection.
 */

CREATE VIEW labelled_connection
    AS SELECT c.sourceid AS sourceid, l.name AS label, c.targetid AS targetid
    FROM connection AS c
    INNER JOIN label AS l
    ON c.label = l.id;

CREATE OR REPLACE RULE lc_insert AS ON INSERT
    TO labelled_connection DO INSTEAD
    WITH lid AS (
        INSERT INTO label (name) VALUES (NEW.label)
            ON CONFLICT ON CONSTRAINT unique_label_name DO NOTHING RETURNING id
    )
    INSERT INTO connection
        SELECT (NEW.sourceid, NEW.targetid, lid)
        FROM lid RETURNING (sourceid, targetid, label);

CREATE OR REPLACE RULE lc_update AS ON UPDATE
    TO labelled_connection DO INSTEAD
    WITH lid AS (
        INSERT INTO label (name) VALUES (NEW.label)
            ON CONFLICT ON CONSTRAINT unique_label_name DO NOTHING RETURNING id
    )
    UPDATE connection SET (sourceid, targetid, label) =
        (SELECT (NEW.sourceid, NEW.targetid, lid)
            FROM lid RETURNING (sourceid, targetid, label))
        WHERE sourceid = OLD.sourceid
        AND targetid = OLD.targetid
        AND label = OLD.label;

CREATE OR REPLACE RULE lc_delete AS ON DELETE
    TO labelled_connection DO INSTEAD
    DELETE FROM connection
        WHERE sourceid = OLD.sourceid
        AND label = OLD.label
        AND targetid = OLD.targetid;
