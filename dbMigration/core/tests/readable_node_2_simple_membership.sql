BEGIN;
SELECT plan(1);

create temporary table visited (id varchar(36) NOT NULL) on commit drop;
create unique index on visited (id);

CREATE FUNCTION insert_node(id varchar(36), data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, deleted, joindate, joinlevel) VALUES (id, data, '4000-01-01', '4000-01-01', 'readwrite');
$$ language sql;

CREATE FUNCTION insert_user(id varchar(36)) RETURNS void
AS $$
begin
    perform insert_node('cpid-' || id);
    perform insert_node(id, jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0, 'channelNodeId', 'cpid-' || id));
end
$$ language plpgsql;

CREATE FUNCTION insert_membership(userid varchar(36), nodeid varchar(36), level accesslevel default 'readwrite') RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (userid, jsonb_build_object('type', 'Member', 'level', level), nodeid);
$$ language sql;


select insert_user('U1');
select insert_node('P1');
select insert_membership('U1', 'P1');

-- membership exists, therefore allowed to see node
SELECT cmp_ok(readable_nodes('U1', array['P1']), '=', array['P1']::varchar(36)[]);

SELECT * FROM finish();
ROLLBACK;
