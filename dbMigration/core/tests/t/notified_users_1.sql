BEGIN;
SELECT plan(13);

create or replace function touuid(id varchar(2)) returns uuid as $$
    select (id || '000000-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

create or replace function user_to_uuid(id varchar(6)) returns uuid as $$
    select (replace(id, 'User', '1111') || '00-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

create or replace function node_to_uuid(id varchar(6)) returns uuid as $$
    select (replace(id, 'Node', '2222') || '00-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

CREATE or replace FUNCTION insert_uuid_node(id uuid, level accesslevel, data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, accesslevel)
        VALUES (id, data, level)
        on conflict(id) do update set accesslevel = excluded.accesslevel, data = excluded.data;
$$ language sql;

CREATE or replace FUNCTION insert_node(id varchar(6), level accesslevel default 'readwrite'::accesslevel, data jsonb default '{}'::jsonb) RETURNS void AS $$
    INSERT INTO node (id, data, accesslevel)
        VALUES (node_to_uuid(id), jsonb_build_object('type', 'PlainText', 'content', id), level)
        on conflict(id) do update set accesslevel = excluded.accesslevel, data = excluded.data;
$$ language sql;

CREATE or replace FUNCTION insert_user(id varchar(6)) RETURNS void AS $$
begin
    perform insert_uuid_node(user_to_uuid(id), 'restricted', jsonb_build_object('type', 'User', 'name', id, 'isImplicit', false, 'revision', 0));
end
$$ language plpgsql;


CREATE or replace FUNCTION insert_membership(userid varchar(6), nodeid varchar(6), level accesslevel default 'readwrite') RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Member', 'level', level), node_to_uuid(nodeid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' NOT IN('Author', 'Before') DO UPDATE set data = EXCLUDED.data
$$ language sql;

CREATE or replace FUNCTION insert_parentship(childid varchar(6), parentid varchar(6), deletedAt text default null) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(childid), jsonb_build_object('type', 'Parent', 'deletedAt', deletedAt), node_to_uuid(parentid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data
$$ language sql;

CREATE or replace FUNCTION insert_notify(nodeid varchar(6), userid varchar(6)) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Notify'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data
$$ language sql;

CREATE or replace FUNCTION insert_before(beforeid varchar(6), parentid varchar(6), afterid varchar(6)) RETURNS void AS $$
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(beforeid), jsonb_build_object('type', 'Before', 'parent', node_to_uuid(parentid)), node_to_uuid(afterid))
        ON CONFLICT(sourceid,(data->>'type'),(data->>'parent'),targetid) WHERE data->>'type'='Before' DO NOTHING
$$ language sql;

-- for comparing arrays element-wise
CREATE or replace FUNCTION array_sort(anyarray) RETURNS anyarray AS $$
    SELECT array_agg(x order by x) FROM unnest($1) x;
$$ LANGUAGE 'sql';


-- test case 1
select insert_user('User11');
select insert_node('Node11', 'readwrite');
select insert_notify('Node11', 'User11');

-- test case 2
select insert_user('User21');
select insert_node('Node21', 'restricted');
select insert_notify('Node21', 'User21');

-- test case 3
select insert_user('User31');
select insert_node('Node31', 'restricted');
select insert_membership('User31', 'Node31', 'readwrite');
select insert_notify('Node31', 'User31');

-- test case 4,5
select insert_user('User41');
select insert_node('Node41', 'restricted');
select insert_node('Node42', 'restricted');
select insert_parentship('Node41', 'Node42');
select insert_membership('User41', 'Node41', 'readwrite');
select insert_notify('Node41', 'User41');

-- test case 6,7
select insert_user('User51');
select insert_node('Node51', 'restricted');
select insert_node('Node52', 'restricted');
select insert_parentship('Node51', 'Node52');
select insert_membership('User51', 'Node51', 'readwrite');
select insert_notify('Node52', 'User51');

-- test case 8,9
select insert_user('User61');
select insert_node('Node61');
select insert_node('Node62', 'restricted');
select insert_parentship('Node61', 'Node62');
select insert_notify('Node61', 'User61');

-- test case 10
select insert_user('User71');
select insert_user('User72');
select insert_node('Node71', 'readwrite');
select insert_node('Node72', 'restricted');
select insert_node('Node73', 'readwrite');
select insert_parentship('Node71', 'Node72');
select insert_parentship('Node72', 'Node73');
select insert_membership('User72', 'Node72', 'readwrite');
select insert_notify('Node73', 'User71');
select insert_notify('Node73', 'User72');

-- test case 11
select insert_user('User81');
select insert_user('User82');
select insert_user('User83');
select insert_user('User84');
select insert_node('Node81');
select insert_node('Node82', 'restricted');
select insert_node('Node83');
select insert_node('Node84', 'restricted');
select insert_node('Node85');
select insert_parentship('Node81', 'Node82');
select insert_parentship('Node82', 'Node83');
select insert_parentship('Node83', 'Node84');
select insert_parentship('Node84', 'Node85');
select insert_membership('User81', 'Node82', 'readwrite');
select insert_membership('User81', 'Node84', 'readwrite');
select insert_membership('User82', 'Node84', 'readwrite');
select insert_membership('User84', 'Node82', 'readwrite');
select insert_notify('Node85', 'User81');
select insert_notify('Node85', 'User82');
select insert_notify('Node85', 'User83');
select insert_notify('Node83', 'User84');

-- test case 12
select insert_user('User91');
select insert_user('User92');
select insert_node('Node91');
select insert_node('Node92');
select insert_node('Node93', 'restricted');
select insert_node('Node94');
select insert_node('Node95');
select insert_parentship('Node91', 'Node95');
select insert_parentship('Node91', 'Node92');
select insert_parentship('Node92', 'Node93');
select insert_parentship('Node93', 'Node94');
select insert_membership('User91', 'Node93', 'readwrite');
select insert_notify('Node94', 'User91');
select insert_notify('Node94', 'User92');
select insert_notify('Node95', 'User92');

-- test cas 13
select insert_user('User01');
select insert_user('User02');
select insert_node('Node01');
select insert_node('Node02');
select insert_node('Node03', 'restricted');
select insert_parentship('Node01', 'Node02');
select insert_parentship('Node02', 'Node03');
select insert_parentship('Node03', 'Node01');
select insert_membership('User01', 'Node03');
select insert_notify('Node01', 'User01');
select insert_notify('Node01', 'User02');

SELECT cmp_ok( --1
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node11')]::uuid[]))), '=', array_sort(array[user_to_uuid('User11'), node_to_uuid('Node11')])
);
SELECT is( --2
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node21')]::uuid[]))), null
);
SELECT cmp_ok( --3
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node31')]::uuid[]))), '=', array_sort(array[user_to_uuid('User31'), node_to_uuid('Node31')])
);
SELECT is( --4
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node42')]::uuid[]))), null
);
SELECT cmp_ok( --5
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node41')]::uuid[]))), '=', array_sort(array[user_to_uuid('User41'), node_to_uuid('Node41')])
);
SELECT is( --6
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node52')]::uuid[]))), null
);
SELECT is( --7
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node51')]::uuid[]))), null
);
SELECT cmp_ok( --8
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node61')]::uuid[]))), '=', array_sort(array[user_to_uuid('User61'), node_to_uuid('Node61')])
);
SELECT is( --9
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node62')]::uuid[]))), null
);
SELECT cmp_ok( --10
    array_sort(array(select array_append(initial_node, userid) from notified_users_search_fast(array[node_to_uuid('Node71')]::uuid[]))), '=', array_sort(array[user_to_uuid('User72'), node_to_uuid('Node71')])
);
SELECT results_eq( --11
    $$ select * from
            notified_users_search_fast(array[
                node_to_uuid('Node81'),
                node_to_uuid('Node82'),
                node_to_uuid('Node83'),
                node_to_uuid('Node84'),
                node_to_uuid('Node85')
            ]::uuid[])
        order by userid $$
    ,
    $$ values
        (user_to_uuid('User81'), array[node_to_uuid('Node81'), node_to_uuid('Node82'), node_to_uuid('Node83'), node_to_uuid('Node84'), node_to_uuid('Node85')]),
        (user_to_uuid('User82'), array[node_to_uuid('Node83'), node_to_uuid('Node84'), node_to_uuid('Node85')]),
        (user_to_uuid('User83'), array[node_to_uuid('Node85')]),
        (user_to_uuid('User84'), array[node_to_uuid('Node81'), node_to_uuid('Node82'), node_to_uuid('Node83')])
    $$
);
SELECT set_eq( --12
    $$ select * from
            notified_users_search_fast(array[
                node_to_uuid('Node91'),
                node_to_uuid('Node92')
            ]::uuid[])
    $$
    ,
    $$ values
        (user_to_uuid('User91'), array[node_to_uuid('Node91'), node_to_uuid('Node92')]),
        (user_to_uuid('User92'), array[node_to_uuid('Node91')])
    $$
);
SELECT set_eq( --13
    $$ select * from
            notified_users_search_fast(array[
                node_to_uuid('Node01'),
                node_to_uuid('Node02'),
                node_to_uuid('Node03')
            ]::uuid[])
    $$
    ,
    $$ values
        (user_to_uuid('User01'), array[node_to_uuid('Node01'), node_to_uuid('Node02'), node_to_uuid('Node03')]),
        (user_to_uuid('User02'), array[node_to_uuid('Node01')])
    $$
);


-- test case X
-- cleanup();
-- select insert_user('A1');
-- select insert_node('B1', 'restricted');
-- select insert_node('B2', 'restricted');
-- select insert_parentship('B1', 'B2');
-- select insert_membership('A1', 'B1', 'readwrite');
-- select insert_before('B1', 'P1', 'N1');
-- select insert_notify('B1', 'A1');


SELECT * FROM finish();
ROLLBACK;
