BEGIN;
SELECT plan(1);

create temporary table visited (id uuid NOT NULL) on commit drop;
create unique index on visited (id);

CREATE FUNCTION insert_node(id uuid, data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, deleted, joindate, joinlevel) VALUES (id, data, '4000-01-01', '4000-01-01', 'readwrite');
$$ language sql;

CREATE FUNCTION insert_user(id varchar(2)) RETURNS void
AS $$
begin
    perform insert_node((id || 'F00000-0000-0000-0000-000000000000')::uuid);
    perform insert_node(touuid(id), jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0, 'channelNodeId', id || 'F00000-0000-0000-0000-000000000000'));
end
$$ language plpgsql;

create function touuid(id varchar(2)) returns uuid as $$
    select (id || '000000-0000-0000-0000-000000000000')::uuid
$$ language sql IMMUTABLE;

CREATE FUNCTION insert_membership(userid uuid, nodeid uuid, level accesslevel default 'readwrite') RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (userid, jsonb_build_object('type', 'Member', 'level', level), nodeid);
$$ language sql;

CREATE FUNCTION insert_parentship(childid uuid, parentid uuid) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (childid, jsonb_build_object('type', 'Parent'), parentid);
$$ language sql;


select insert_node(touuid('B1'));
select insert_node(touuid('B2'));

select insert_parentship(touuid('B2'), touuid('B1'));


select insert_user('A1');

select insert_membership(touuid('A1'), touuid('B1'));

-- can read child because of membership on parent
SELECT cmp_ok(readable_nodes(touuid('A1'), array[touuid('B1'), touuid('B2')]), '=', array[touuid('B2'), touuid('B1')]::uuid[]);

SELECT * FROM finish();
ROLLBACK;
