BEGIN;
SELECT plan(8);

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


-- for comparing arrays element-wise
CREATE OR REPLACE FUNCTION array_sort(anyarray) RETURNS anyarray AS $$
SELECT array_agg(x order by x) FROM unnest($1) x;
$$ LANGUAGE 'sql';


SELECT cmp_ok(graph_page_nodes(array[touuid('B1')], array[]::uuid[], touuid('A1')), '=', array[]::uuid[]);
drop table visited;
SELECT cmp_ok(graph_page_nodes(array[touuid('B2')], array[]::uuid[], touuid('A2')), '=', array[]::uuid[]);
drop table visited;
SELECT cmp_ok(graph_page_nodes(array[touuid('B3')], array[]::uuid[], touuid('A3')), '=', array[]::uuid[]);
drop table visited;
SELECT cmp_ok(array_sort(graph_page_nodes(array[touuid('B4')], array[]::uuid[], touuid('A4'))), '=', array_sort(array[touuid('B4'), touuid('C4')]));
drop table visited;
SELECT cmp_ok(array_sort(graph_page_nodes(array[touuid('B5')], array[]::uuid[], touuid('A5'))), '=', array_sort(array[touuid('C5'), touuid('B5')]));
drop table visited;
SELECT cmp_ok(array_sort(graph_page_nodes(array[touuid('B6')], array[]::uuid[], touuid('A6'))), '=', array_sort(array[touuid('B6'), touuid('C6')]));
drop table visited;
SELECT cmp_ok(graph_page_nodes(array[touuid('B7')], array[]::uuid[], touuid('A7')), '=', array[]::uuid[]);
drop table visited;
SELECT cmp_ok(array_sort(graph_page_nodes(array[touuid('B8')], array[]::uuid[], touuid('A8'))), '=', array_sort(array[touuid('B8'), touuid('C8')]));
drop table visited;

SELECT * FROM finish();
ROLLBACK;
