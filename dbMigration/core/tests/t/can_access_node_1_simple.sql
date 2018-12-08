BEGIN;
SELECT plan(16);

-- suppress cascade notices from cleanup()
SET client_min_messages TO WARNING;

create or replace function user_to_uuid(id varchar(2)) returns uuid as $$
    select ('05e200' || id || '-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

create or replace function node_to_uuid(id varchar(2)) returns uuid as $$
    select ('90de00' || id || '-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

CREATE or replace FUNCTION insert_uuid_node(nid uuid, level accesslevel, data jsonb default '{}'::jsonb, role jsonb default '{"type": "Message"}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, role, accesslevel)
        VALUES (nid, data, role, level)
        on conflict(id) do update set accesslevel = excluded.accesslevel, data = excluded.data, role = excluded.data;
$$ language sql;

CREATE or replace FUNCTION node(nid varchar(2), level accesslevel default 'readwrite'::accesslevel, role jsonb default '{"type": "Message"}'::jsonb) RETURNS void AS $$
begin
    INSERT INTO node (id, data, role, accesslevel)
        VALUES (node_to_uuid(nid), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid(nid)), role, level)
        on conflict(id) do update set accesslevel = excluded.accesslevel, data = excluded.data, role = excluded.role;
end
$$ language plpgsql;

CREATE or replace FUNCTION usernode(id varchar(6)) RETURNS void AS $$
begin
    perform insert_uuid_node(user_to_uuid(id), 'restricted', jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0));
end
$$ language plpgsql;


CREATE or replace FUNCTION member(userid varchar(2), nodeid varchar(2), level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Member', 'level', level), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION member(userid varchar(2), nodeid uuid, level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Member', 'level', level), nodeid)
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;


CREATE or replace FUNCTION author(userid varchar(2), nodeid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Author'), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION expanded(userid varchar(2), nodeid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Expanded'), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION parent(childid varchar(2), parentid varchar(2), deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(childid), jsonb_build_object('type', 'Parent', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), node_to_uuid(parentid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> 'Author'::text DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION parent(childid uuid, parentid uuid, deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (childid, jsonb_build_object('type', 'Parent', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), parentid)
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> 'Author'::text DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION notify(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Notify'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> 'Author'::text DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION pinned(userid varchar(2), nodeid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Pinned'), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> 'Author'::text DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION assigned(userid varchar(2), nodeid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Assigned'), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;


CREATE or replace FUNCTION cleanup() RETURNS void AS $$
begin
    truncate node cascade;
end
$$ language plpgsql;


-- for comparing arrays element-wise
CREATE or replace FUNCTION array_sort(anyarray) RETURNS anyarray AS $$
    SELECT array_agg(x order by x) FROM unnest($1) x;
$$ LANGUAGE 'sql';




-- IMPORTANT:
-- exactly the same test cases as in GraphSpec
-- when changing things, make sure to change them for the Graph as well.

-- case 1:
select cleanup();
select usernode('A1'); -- user
select node('B1', 'restricted'); -- node with permission
select member('A1', 'B1', 'restricted'); -- membership with level

-- single node
SELECT cmp_ok(can_access_node(user_to_uuid('A1'), node_to_uuid('B1')), '=', false);
-- array of nodes, returning conflicting nodes
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A1'), array[node_to_uuid('B1')]), '=', array[node_to_uuid('B1')]);


-- case 2:
select cleanup();
select usernode('A2');
select node('B2', 'readwrite');
select member('A2', 'B2', 'restricted');

SELECT cmp_ok(can_access_node(user_to_uuid('A2'), node_to_uuid('B2')), '=', false);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A2'), array[node_to_uuid('B2')]), '=', array[node_to_uuid('B2')]);

-- case 3:
select cleanup();
select usernode('A3');
select node('B3', null);
select member('A3', 'B3', 'restricted');

SELECT cmp_ok(can_access_node(user_to_uuid('A3'), node_to_uuid('B3')), '=', false);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A3'), array[node_to_uuid('B3')]), '=', array[node_to_uuid('B3')]);


-- case 4:
select cleanup();
select usernode('A4');
select node('B4', 'restricted');
select member('A4', 'B4', 'readwrite');

SELECT cmp_ok(can_access_node(user_to_uuid('A4'), node_to_uuid('B4')), '=', true);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A4'), array[node_to_uuid('B4')]), '=', array[]::uuid[]);


-- case 5:
select cleanup();
select usernode('A5');
select node('B5', 'readwrite');
select member('A5', 'B5', 'readwrite');

SELECT cmp_ok(can_access_node(user_to_uuid('A5'), node_to_uuid('B5')), '=', true);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A5'), array[node_to_uuid('B5')]), '=', array[]::uuid[]);


-- case 6:
select cleanup();
select usernode('A6');
select node('B6', null);
select member('A6', 'B6', 'readwrite');

SELECT cmp_ok(can_access_node(user_to_uuid('A6'), node_to_uuid('B6')), '=', true);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A6'), array[node_to_uuid('B6')]), '=', array[]::uuid[]);


-- case 7:
select cleanup();
select usernode('A7');
select node('B7', 'restricted');

SELECT cmp_ok(can_access_node(user_to_uuid('A7'), node_to_uuid('B7')), '=', false);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A7'), array[node_to_uuid('B7')]), '=', array[node_to_uuid('B7')]);


-- case 8:
select cleanup();
select usernode('A8');
select node('B8', 'readwrite');

SELECT cmp_ok(can_access_node(user_to_uuid('A8'), node_to_uuid('B8')), '=', true);
SELECT cmp_ok(inaccessible_nodes(user_to_uuid('A8'), array[node_to_uuid('B8')]), '=', array[]::uuid[]);


SELECT * FROM finish();
ROLLBACK;
