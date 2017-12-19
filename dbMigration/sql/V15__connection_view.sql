/** This migration creates a view for connections queries.
 * The goal is to eliminate consequtive joins of labels for rawconnection.
 * Implemented using triggers because rules have certain limitations and
 * therefore seem to be not applicable since:
 *   - WITH statement has no access to NEW, OLD variables.
 *   - An INSERT containing an ON CONFLICT clause cannot be used on tables that have
 *     either INSERT or UPDATE rules. Therefore it is not possible to do s.th. like
 *     INSERT INTO label (name) VALUES (_name) ON CONFLICT ON CONSTRAINT unique_label_name DO NOTHING
 *   - Could not find a way to construct proper returning structure with rules
 */

CREATE VIEW connection AS
    SELECT c.sourceid AS sourceid, l.name AS label, c.targetid AS targetid
        FROM rawconnection AS c
        INNER JOIN label AS l
        ON c.label = l.id;

CREATE OR REPLACE FUNCTION get_label_id(_name TEXT)
RETURNS INTEGER AS $$
    BEGIN
        RETURN (SELECT id FROM label WHERE name = _name);
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION insert_label(_name TEXT, OUT _lid INTEGER)
RETURNS INTEGER AS $$
    BEGIN
        SELECT id FROM label WHERE name = _name INTO _lid;
        IF NOT FOUND THEN
            INSERT INTO label (name) VALUES (_name) RETURNING id INTO _lid;
        END IF;
    END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION vc_insert()
    RETURNS TRIGGER AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            _lid = insert_label(NEW.label);

        INSERT INTO rawconnection (sourceid, targetid, label)
            SELECT NEW.sourceid, NEW.targetid, _lid
                WHERE NOT EXISTS (
                    SELECT TRUE FROM rawconnection
                        WHERE sourceid = NEW.sourceid
                        AND targetid = NEW.targetid
                        AND label = _lid
                );

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Inserted % row(s) FROM rawconnection', row_count;

            RETURN NEW;
        END;
    $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION vc_update()
    RETURNS TRIGGER AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            IF NEW.label <> OLD.label THEN
                _lid = insert_label(NEW.label);
            END IF;

            UPDATE rawconnection SET (sourceid, targetid, label) = (NEW.sourceid, NEW.targetid, _lid)
                WHERE sourceid = OLD.sourceid
                AND targetid = OLD.targetid
                AND label = OLD.label;

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Updated % row(s) FROM rawconnection', row_count;

            RETURN NEW.sourceid, _lid, NEW.targetid;
        END;
    $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION vc_delete()
    RETURNS TRIGGER AS $$
        DECLARE
            _lid INTEGER;
            row_count INTEGER;
        BEGIN
            -- label
            _lid = get_label_id(OLD.label);

            -- delete
            DELETE FROM rawconnection
                WHERE sourceid = OLD.sourceid AND label = _lid AND targetid = OLD.targetid;

            GET DIAGNOSTICS row_count = ROW_COUNT;
            RAISE NOTICE 'Deleted % row(s) FROM rawconnection', row_count;

            RETURN OLD;
        END;
    $$ LANGUAGE plpgsql;

CREATE TRIGGER vct_insert
    INSTEAD OF INSERT
    ON connection
    FOR EACH ROW
    EXECUTE PROCEDURE vc_insert();

CREATE TRIGGER vct_update
    INSTEAD OF UPDATE
    ON connection
    FOR EACH ROW
    EXECUTE PROCEDURE vc_update();

CREATE TRIGGER vct_delete
    INSTEAD OF DELETE
    ON connection
    FOR EACH ROW
    EXECUTE PROCEDURE vc_delete();
