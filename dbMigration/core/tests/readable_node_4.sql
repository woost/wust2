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

CREATE FUNCTION insert_parentship(childid varchar(36), parentid varchar(36)) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid) VALUES (childid, jsonb_build_object('type', 'Parent'), parentid);
$$ language sql;


select insert_node('P1');
select insert_node('P2');
select insert_node('P3');
select insert_node('P4');
select insert_node('P5');
select insert_node('P6');

select insert_parentship('P2', 'P3');
select insert_parentship('P2', 'P1');
select insert_parentship('P6', 'P5');


select insert_user('U1');
select insert_user('U2');

select insert_membership('U1', 'P1');
select insert_membership('U1', 'P4');
select insert_membership('U2', 'P4');
select insert_membership('U2', 'P5');
select insert_membership('U2', 'P1');

SELECT cmp_ok(readable_nodes('U1', array['P2','P4','P6']), '=', array['P2', 'P4']::varchar(36)[]);

SELECT * FROM finish();
ROLLBACK;
