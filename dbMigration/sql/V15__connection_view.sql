/** This migration creates a view for connections queries.
 * The goal is to eliminate consequtive joins of labels for rawconnection.
 */

CREATE VIEW connection
    AS SELECT c.sourceid AS sourceid, l.name AS label, c.targetid AS targetid
    FROM rawconnection AS c
    INNER JOIN label AS l
    ON c.label = l.id;

CREATE OR REPLACE FUNCTION get_label_id(_name TEXT)
RETURNS INTEGER AS $$
    BEGIN
        RETURN (SELECT id FROM label WHERE name = _name);
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION insert_label(_name TEXT, OUT _lid INTEGER, OUT _newLabel BOOLEAN)
RETURNS INTEGER AS $$
    BEGIN
        -- TODO: Is there a more elegant way to achive that?
        -- WITH statement has no access to NEW, OLD variables
        -- INSERT INTO label (name) VALUES (_name) ON CONFLICT ON CONSTRAINT unique_label_name DO NOTHING;
        -- RETURN get_label_id(_name);
        SELECT id FROM label WHERE name = _name INTO _lid;
        IF NOT FOUND THEN
            _newLabel = false;
            INSERT INTO label (name) VALUES (_name) RETURNING id INTO _lid;
        END IF;
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE RULE lc_insert AS ON INSERT
    TO connection DO INSTEAD
    INSERT INTO rawconnection (sourceid, targetid, label)
        VALUES (NEW.sourceid, NEW.targetid, insert_label(NEW.label))
        RETURNING *;

CREATE OR REPLACE RULE lc_update AS ON UPDATE
    TO connection DO INSTEAD
    UPDATE rawconnection SET (sourceid, targetid, label) = (NEW.sourceid, NEW.targetid, insert_label(NEW.label))
        WHERE sourceid = OLD.sourceid
        AND targetid = OLD.targetid
        AND label = get_label_id(OLD.label);

CREATE OR REPLACE RULE lc_delete AS ON DELETE
    TO connection DO INSTEAD
    DELETE FROM rawconnection
        WHERE sourceid = OLD.sourceid
        AND targetid = OLD.targetid
        AND label = get_label_id(OLD.label);
