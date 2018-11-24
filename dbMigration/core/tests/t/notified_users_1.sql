BEGIN;
SELECT plan(14);

create or replace function user_to_uuid(id varchar(2)) returns uuid as $$
    select ('1111' || id || '00-0000-0000-0000-000000000000')::uuid;
$$ language sql IMMUTABLE;

create or replace function node_to_uuid(id varchar(2)) returns uuid as $$
    select ('2222' || id || '00-0000-0000-0000-000000000000')::uuid;
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
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' NOT IN('Author', 'Before') DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION parent(childid varchar(2), parentid varchar(2), deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(childid), jsonb_build_object('type', 'Parent', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), node_to_uuid(parentid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION parent(childid uuid, parentid uuid, deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (childid, jsonb_build_object('type', 'Parent', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), parentid)
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION notify(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Notify'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data->>'type')::text <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION before(beforeid varchar(2), parentid varchar(2), afterid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(beforeid), jsonb_build_object('type', 'Before', 'parent', node_to_uuid(parentid)), node_to_uuid(afterid))
        ON CONFLICT(sourceid,(data->>'type'),(data->>'parent'),targetid) WHERE data->>'type'='Before' DO NOTHING;
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


-- request empty page
select cleanup();

-- test case 1
select usernode('11');
select node('11', 'readwrite');
select notify('11', '11');

SELECT set_eq( --1
    $$
        select * from notified_users_search_fast(array[node_to_uuid('11')]::uuid[])
    $$,
    $$ values
        (user_to_uuid('11'), array[node_to_uuid('11')], node_to_uuid('11'))
    $$
);

-- test case 2
select cleanup();
select usernode('21');
select node('21', 'restricted');
select notify('21', '21');

SELECT is_empty( --2
    $$ select * from notified_users_search_fast(array[node_to_uuid('21')]::uuid[]) $$
);

-- test case 3
select cleanup();
select usernode('31');
select node('31', 'restricted');
select member('31', '31', 'readwrite');
select notify('31', '31');

SELECT set_eq( --3
    $$
        select * from notified_users_search_fast(array[node_to_uuid('31')]::uuid[])
    $$,
    $$ values
        (user_to_uuid('31'), array[node_to_uuid('31')], node_to_uuid('31'))
    $$
);

-- test case 4,5
select cleanup();
select usernode('41');
select node('41', 'restricted');
select node('42', 'restricted');
select parent('41', '42');
select member('41', '41', 'readwrite');
select notify('41', '41');

SELECT is_empty( --4
    $$ select * from notified_users_search_fast(array[node_to_uuid('42')]::uuid[]) $$
);
SELECT set_eq( --5
    $$
        select * from notified_users_search_fast(array[node_to_uuid('41')]::uuid[])
    $$,
    $$ values
        (user_to_uuid('41'), array[node_to_uuid('41')], node_to_uuid('41'))
    $$
);

-- test case 6,7
select cleanup();
select usernode('51');
select node('51', 'restricted');
select node('52', 'restricted');
select parent('51', '52');
select member('51', '51', 'readwrite');
select notify('52', '51');

SELECT is_empty( --6
    $$ select * from notified_users_search_fast(array[node_to_uuid('52')]::uuid[]) $$
);
SELECT is_empty( --7
    $$ select * from notified_users_search_fast(array[node_to_uuid('51')]::uuid[]) $$
);

-- test case 8,9
select cleanup();
select usernode('61');
select node('61');
select node('62', 'restricted');
select parent('61', '62');
select notify('61', '61');

SELECT set_eq( --8
    $$
        select * from notified_users_search_fast(array[node_to_uuid('61')]::uuid[])
    $$,
    $$ values
        (user_to_uuid('61'), array[node_to_uuid('61')], node_to_uuid('61'))
    $$
);
SELECT is_empty( --9
    $$ select * from notified_users_search_fast(array[node_to_uuid('62')]::uuid[]) $$
);

-- test case 10
select cleanup();
select usernode('71');
select usernode('72');
select node('71', 'readwrite');
select node('72', 'restricted');
select node('73', 'readwrite');
select parent('71', '72');
select parent('72', '73');
select member('72', '72', 'readwrite');
select notify('73', '71');
select notify('73', '72');

SELECT set_eq( --10
    $$
        select * from notified_users_search_fast(array[node_to_uuid('71')]::uuid[])
    $$,
    $$ values
        (user_to_uuid('72'), array[node_to_uuid('71')], node_to_uuid('73'))
    $$
);

-- test case 11
select cleanup();
select usernode('81');
select usernode('82');
select usernode('83');
select usernode('84');
select node('81');
select node('82', 'restricted');
select node('83');
select node('84', 'restricted');
select node('85');
select parent('81', '82');
select parent('82', '83');
select parent('83', '84');
select parent('84', '85');
select member('81', '82', 'readwrite');
select member('81', '84', 'readwrite');
select member('82', '84', 'readwrite');
select member('84', '82', 'readwrite');
select notify('85', '81');
select notify('85', '82');
select notify('85', '83');
select notify('83', '84');

SELECT set_eq( --11
    $$ select userid, array_sort(initial_nodes), subscribed_node from
            notified_users_search_fast(array[
                node_to_uuid('81'),
                node_to_uuid('82'),
                node_to_uuid('83'),
                node_to_uuid('84'),
                node_to_uuid('85')
            ]::uuid[])
    $$
    ,
    $$ values
        (user_to_uuid('81'), array[node_to_uuid('81'), node_to_uuid('82'), node_to_uuid('83'), node_to_uuid('84'), node_to_uuid('85')], node_to_uuid('85')),
        (user_to_uuid('82'), array[node_to_uuid('83'), node_to_uuid('84'), node_to_uuid('85')], node_to_uuid('85')),
        (user_to_uuid('83'), array[node_to_uuid('85')], node_to_uuid('85')),
        (user_to_uuid('84'), array[node_to_uuid('81'), node_to_uuid('82'), node_to_uuid('83')], node_to_uuid('83'))
    $$
);

-- test case 12
select cleanup();
select usernode('91');
select usernode('92');
select node('91');
select node('92');
select node('93', 'restricted');
select node('94');
select node('95');
select parent('91', '95');
select parent('91', '92');
select parent('92', '93');
select parent('93', '94');
select member('91', '93', 'readwrite');
select notify('94', '91');
select notify('94', '92');
select notify('95', '92');

SELECT set_eq( --12
    $$ select userid, array_sort(initial_nodes), subscribed_node from
            notified_users_search_fast(array[
                node_to_uuid('91'),
                node_to_uuid('92')
            ]::uuid[])
    $$
    ,
    $$ values
        (user_to_uuid('91'), array[node_to_uuid('91'), node_to_uuid('92')], node_to_uuid('94')),
        (user_to_uuid('92'), array[node_to_uuid('91')], node_to_uuid('95'))
    $$
);

-- test case 13
select cleanup();
select usernode('01');
select usernode('02');
select node('01');
select node('02');
select node('03', 'restricted');
select parent('01', '02');
select parent('02', '03');
select parent('03', '01');
select member('01', '03');
select notify('01', '01');
select notify('01', '02');

SELECT set_eq( --13
    $$ select userid, array_sort(initial_nodes), subscribed_node from
            notified_users_search_fast(array[
                node_to_uuid('01'),
                node_to_uuid('02'),
                node_to_uuid('03')
            ]::uuid[])
    $$
    ,
    $$ values
        (user_to_uuid('01'), array[node_to_uuid('01'), node_to_uuid('02'), node_to_uuid('03')], node_to_uuid('01')),
        (user_to_uuid('02'), array[node_to_uuid('01')], node_to_uuid('01'))
    $$
);

-- test case 14: respect deleted edges (ignore them)
select cleanup();
select usernode('01');
select usernode('02');
select node('01');
select node('02');
select node('03');
select parent('01', '02');
select parent('01', '03', (now_utc() - interval '1' hour)::timestamp);
select notify('02', '01');
select notify('03', '02');

SELECT set_eq( --13
    $$ select userid, array_sort(initial_nodes), subscribed_node from
            notified_users_search_fast(array[
                node_to_uuid('01')
            ])
    $$
    ,
    $$ values
        (user_to_uuid('01'), array[node_to_uuid('01')], node_to_uuid('02'))
    $$
);

-- -- test case 14: avoid multiple notifications in multiple subscriptions single node
-- select cleanup();
-- select usernode('01');
-- select usernode('02');
-- select node('01');
-- select node('02');
-- select node('03');
-- select node('04');
-- select parent('01', '02');
-- select parent('02', '03');
-- select parent('03', '04');
-- select notify('01', '01');
-- select notify('03', '01');
-- select notify('02', '02');
-- select notify('04', '02');

-- SELECT set_eq( --14
--     $$ select userid, array_sort(initial_nodes), subscribed_node from
--             notified_users_search_fast(array[
--                 node_to_uuid('02')
--             ]::uuid[])
--     $$
--     ,
--     $$ values
--         (user_to_uuid('01'), array[node_to_uuid('02')], node_to_uuid('03')),
--         (user_to_uuid('02'), array[node_to_uuid('02')], node_to_uuid('02'))
--     $$
-- );

-- -- test case 15: avoid multiple notifications in multiple subscriptions multiple nodes
-- select cleanup();
-- select usernode('01');
-- select usernode('02');
-- select node('01');
-- select node('02');
-- select node('03');
-- select node('04');
-- select parent('01', '02');
-- select parent('02', '03');
-- select parent('03', '04');
-- select notify('01', '01');
-- select notify('03', '01');
-- select notify('02', '02');
-- select notify('04', '02');

-- SELECT set_eq( --15
--     $$ select userid, array_sort(initial_nodes), subscribed_node from
--             notified_users_search_fast(array[
--                 node_to_uuid('01'),
--                 node_to_uuid('02'),
--                 node_to_uuid('03'),
--                 node_to_uuid('04')
--             ]::uuid[])
--     $$
--     ,
--     $$ values
--         (user_to_uuid('01'), array[node_to_uuid('01')], node_to_uuid('01')),
--         (user_to_uuid('01'), array[node_to_uuid('02'), node_to_uuid('03')], node_to_uuid('03')),
--         (user_to_uuid('02'), array[node_to_uuid('01'), node_to_uuid('02')], node_to_uuid('02')),
--         (user_to_uuid('02'), array[node_to_uuid('03'), node_to_uuid('04')], node_to_uuid('04'))
--     $$
-- );


-- test case X
-- cleanup();
-- select usernode('A1');
-- select node('B1', 'restricted');
-- select node('B2', 'restricted');
-- select parent('B1', 'B2');
-- select member('A1', 'B1', 'readwrite');
-- select before('B1', 'P1', 'N1');
-- select notify('B1', 'A1');


SELECT * FROM finish();
ROLLBACK;
