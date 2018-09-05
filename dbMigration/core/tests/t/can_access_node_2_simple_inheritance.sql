BEGIN;
SELECT plan(16);

CREATE FUNCTION insert_node(id uuid, level accesslevel, data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, accesslevel) VALUES (id, data, level);
$$ language sql;

CREATE FUNCTION insert_user(id varchar(2)) RETURNS void
AS $$
begin
    perform insert_node((id || 'F00000-0000-0000-0000-000000000000')::uuid, 'restricted');
    perform insert_node(touuid(id), 'restricted', jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0, 'channelNodeId', id || 'F00000-0000-0000-0000-000000000000'));
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



-- IMPORTANT:
-- exactly the same test cases as in GraphSpec
-- when changing things, make sure to change them for the Graph as well.

-- case 1:
select insert_user('A1'); -- user
select insert_node(touuid('B1'), 'restricted'); -- node with permission
select insert_node(touuid('C1'), NULL); -- node with permission inheritance
select insert_parentship(touuid('C1'), touuid('B1')); -- inheritance happens via this parent edge
select insert_membership(touuid('A1'), touuid('B1'), 'restricted'); -- membership with level

-- case 2:
select insert_user('A2');
select insert_node(touuid('B2'), 'readwrite');
select insert_node(touuid('C2'), null);
select insert_parentship(touuid('C2'), touuid('B2'));
select insert_membership(touuid('A2'), touuid('B2'), 'restricted');

-- case 3:
select insert_user('A3');
select insert_node(touuid('B3'), null);
select insert_node(touuid('C3'), null);
select insert_parentship(touuid('C3'), touuid('B3'));
select insert_membership(touuid('A3'), touuid('B3'), 'restricted');



-- case 4:
select insert_user('A4');
select insert_node(touuid('B4'), 'restricted');
select insert_node(touuid('C4'), null);
select insert_parentship(touuid('C4'), touuid('B4'));
select insert_membership(touuid('A4'), touuid('B4'), 'readwrite');

-- case 5:
select insert_user('A5');
select insert_node(touuid('B5'), 'readwrite');
select insert_node(touuid('C5'), null);
select insert_parentship(touuid('C5'), touuid('B5'));
select insert_membership(touuid('A5'), touuid('B5'), 'readwrite');

-- case 6:
select insert_user('A6');
select insert_node(touuid('B6'), null);
select insert_node(touuid('C6'), null);
select insert_parentship(touuid('C6'), touuid('B6'));
select insert_membership(touuid('A6'), touuid('B6'), 'readwrite');



-- case 7:
select insert_user('A7');
select insert_node(touuid('B7'), 'restricted');
select insert_node(touuid('C7'), null);
select insert_parentship(touuid('C7'), touuid('B7'));

-- case 8:
select insert_user('A8');
select insert_node(touuid('B8'), 'readwrite');
select insert_node(touuid('C8'), null);
select insert_parentship(touuid('C8'), touuid('B8'));


-- single node
SELECT cmp_ok(can_access_node(touuid('A1'), touuid('C1')), '=', false);
SELECT cmp_ok(can_access_node(touuid('A2'), touuid('C2')), '=', false);
SELECT cmp_ok(can_access_node(touuid('A3'), touuid('C3')), '=', false);
SELECT cmp_ok(can_access_node(touuid('A4'), touuid('C4')), '=', true);
SELECT cmp_ok(can_access_node(touuid('A5'), touuid('C5')), '=', true);
SELECT cmp_ok(can_access_node(touuid('A6'), touuid('C6')), '=', true);
SELECT cmp_ok(can_access_node(touuid('A7'), touuid('C7')), '=', false);
SELECT cmp_ok(can_access_node(touuid('A8'), touuid('C8')), '=', true);

-- array of nodes, returning conflicting nodes
SELECT cmp_ok(inaccessible_nodes(touuid('A1'), array[touuid('C1')]), '=', array[touuid('C1')]);
SELECT cmp_ok(inaccessible_nodes(touuid('A2'), array[touuid('C2')]), '=', array[touuid('C2')]);
SELECT cmp_ok(inaccessible_nodes(touuid('A3'), array[touuid('C3')]), '=', array[touuid('C3')]);
SELECT cmp_ok(inaccessible_nodes(touuid('A4'), array[touuid('C4')]), '=', array[]::uuid[]);
SELECT cmp_ok(inaccessible_nodes(touuid('A5'), array[touuid('C5')]), '=', array[]::uuid[]);
SELECT cmp_ok(inaccessible_nodes(touuid('A6'), array[touuid('C6')]), '=', array[]::uuid[]);
SELECT cmp_ok(inaccessible_nodes(touuid('A7'), array[touuid('C7')]), '=', array[touuid('C7')]);
SELECT cmp_ok(inaccessible_nodes(touuid('A8'), array[touuid('C8')]), '=', array[]::uuid[]);

SELECT * FROM finish();
ROLLBACK;
