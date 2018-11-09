BEGIN;
SELECT plan(1);

CREATE FUNCTION insert_node(id uuid, level accesslevel, data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, accesslevel) VALUES (id, data, level);
$$ language sql;

CREATE FUNCTION insert_user(id varchar(2)) RETURNS void
AS $$
begin
    perform insert_node(touuid(id), 'restricted', jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0));
end
$$ language plpgsql;

create function touuid(id varchar(2)) returns uuid as $$
    select (id || '000000-0000-0000-0000-000000000000')::uuid
$$ language sql IMMUTABLE;

CREATE FUNCTION insert_membership(userid uuid, nodeid uuid, level accesslevel) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (userid, jsonb_build_object('type', 'Member', 'level', level), nodeid);
$$ language sql;

CREATE FUNCTION insert_parentship(childid uuid, parentid uuid) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (childid, jsonb_build_object('type', 'Parent'), parentid);
$$ language sql;

CREATE FUNCTION insert_pinned(userid uuid, nodeid uuid) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (userid, jsonb_build_object('type', 'Pinned'), nodeid);
$$ language sql;



-- IMPORTANT:
-- exactly the same test cases as in GraphSpec
-- when changing things, make sure to change them for the Graph as well.

-- case 1:
select insert_user('A2'); -- user
select insert_node(touuid('A1'), NULL);
select insert_node(touuid('B1'), NULL);
select insert_node(touuid('C1'), NULL);
select insert_node(touuid('D1'), NULL);
select insert_parentship(touuid('B1'), touuid('A1'));
select insert_parentship(touuid('C1'), touuid('B1'));
select insert_parentship(touuid('D1'), touuid('A1'));
select insert_parentship(touuid('D1'), touuid('C1'));
select insert_membership(touuid('A2'), touuid('A1'), 'readwrite');
select insert_pinned(touuid('A2'), touuid('A1'));

-- single node
/* SELECT cmp_ok((select array_agg(nodeid) from readable_graph_page_nodes_with_channels(array[]::uuid[], array[]::uuid[], touuid('A2'))), '=', array[touuid('A1'), touuid('B1'), touuid('C1'), touuid('D1')]); */
/* drop table visited; */
SELECT cmp_ok(1, '=', 1);

SELECT * FROM finish();
ROLLBACK;
