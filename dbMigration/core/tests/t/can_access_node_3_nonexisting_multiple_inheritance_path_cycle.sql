BEGIN;
SELECT plan(18);

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


-- multiple inheritance: max wins
select insert_user('A1'); -- user
select insert_node(touuid('B1'), 'restricted'); -- node without permission
select insert_node(touuid('C1'), 'readwrite'); -- node with permission
select insert_node(touuid('D1'), NULL); -- node with permission multiple inheritance
select insert_parentship(touuid('D1'), touuid('B1')); -- inheritance happens via this parent edge
select insert_parentship(touuid('D1'), touuid('C1')); -- inheritance happens via this parent edge

SELECT cmp_ok(can_access_node(touuid('A1'), touuid('D1')), '=', true);
SELECT cmp_ok(inaccessible_nodes(touuid('A1'), array[touuid('D1')]), '=', array[]::uuid[]);


-- long inheritance chain: readwrite
select insert_user('A2');
select insert_node(touuid('B2'), 'readwrite');
select insert_node(touuid('C2'), NULL);
select insert_node(touuid('D2'), NULL);
select insert_parentship(touuid('C2'), touuid('B2'));
select insert_parentship(touuid('D2'), touuid('C2'));

SELECT cmp_ok(can_access_node(touuid('A2'), touuid('D2')), '=', true);
SELECT cmp_ok(inaccessible_nodes(touuid('A2'), array[touuid('D2')]), '=', array[]::uuid[]);

-- long inheritance chain: restricted
select insert_user('A3');
select insert_node(touuid('B3'), 'restricted');
select insert_node(touuid('C3'), NULL);
select insert_node(touuid('D3'), NULL);
select insert_parentship(touuid('C3'), touuid('B3'));
select insert_parentship(touuid('D3'), touuid('C3'));

SELECT cmp_ok(can_access_node(touuid('A3'), touuid('D3')), '=', false);
SELECT cmp_ok(inaccessible_nodes(touuid('A3'), array[touuid('D3')]), '=', array[touuid('D3')]::uuid[]);

-- inheritance cycle: readwrite
select insert_user('A4');
select insert_node(touuid('B4'), 'readwrite');
select insert_node(touuid('C4'), NULL);
select insert_node(touuid('D4'), NULL);
select insert_parentship(touuid('C4'), touuid('B4'));
select insert_parentship(touuid('D4'), touuid('C4'));
select insert_parentship(touuid('C4'), touuid('D4'));

SELECT cmp_ok(can_access_node(touuid('A4'), touuid('D4')), '=', true);
SELECT cmp_ok(can_access_node(touuid('A4'), touuid('C4')), '=', true);
SELECT cmp_ok(inaccessible_nodes(touuid('A4'), array[touuid('D4')]), '=', array[]::uuid[]);
SELECT cmp_ok(inaccessible_nodes(touuid('A4'), array[touuid('C4')]), '=', array[]::uuid[]);

-- inheritance cycle: restricted
select insert_user('A5');
select insert_node(touuid('B5'), 'restricted');
select insert_node(touuid('C5'), NULL);
select insert_node(touuid('D5'), NULL);
select insert_parentship(touuid('C5'), touuid('B5'));
select insert_parentship(touuid('D5'), touuid('C5'));
select insert_parentship(touuid('C5'), touuid('D5'));

SELECT cmp_ok(can_access_node(touuid('A5'), touuid('D5')), '=', false);
SELECT cmp_ok(can_access_node(touuid('A5'), touuid('C5')), '=', false);
SELECT cmp_ok(inaccessible_nodes(touuid('A5'), array[touuid('D5')]), '=', array[touuid('D5')]::uuid[]);
SELECT cmp_ok(inaccessible_nodes(touuid('A5'), array[touuid('C5')]), '=', array[touuid('C5')]::uuid[]);


-- non-existing nodes
select insert_user('A6');

SELECT cmp_ok(can_access_node(touuid('A6'), touuid('D6')), '=', true);
SELECT cmp_ok(inaccessible_nodes(touuid('A6'), array[touuid('D6')]), '=', array[]::uuid[]);


-- non-existing user
-- we assume that a user exist in any cases and therefore we do not handle this explicitly


-- inherit without any parent
select insert_user('A7'); -- user
select insert_node(touuid('B7'), NULL); -- node with permission multiple inheritance

SELECT cmp_ok(can_access_node(touuid('A7'), touuid('B7')), '=', false);
SELECT cmp_ok(inaccessible_nodes(touuid('A7'), array[touuid('B7')]), '=', array[touuid('B7')]::uuid[]);



SELECT * FROM finish();
ROLLBACK;
