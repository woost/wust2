BEGIN;
SELECT plan(8);

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

CREATE or replace FUNCTION invite(userid varchar(2), nodeid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (user_to_uuid(userid), jsonb_build_object('type', 'Invite'), node_to_uuid(nodeid))
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



-- graph_page with induced subgraph
select cleanup();

select node('01');
select usernode('0A');
select member('0A', '01');

select set_eq(
    $$ select * from
            graph_page( array[ node_to_uuid('01') ]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[node_to_uuid('01')]::uuid[], array['{"type": "Member", "level": "readwrite"}']::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[]::uuid[], array[]::text[])
    $$
);


-- single pinned node and user
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select parent('B1', 'A1');
select node('C1', NULL); select parent('C1', 'B1');
select node('D1', NULL); select parent('D1', 'A1'); select parent('D1', 'C1');

select usernode('A2');
select member('A2', 'A1', 'readwrite');
select pinned('A2', 'A1');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'), jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[node_to_uuid('A1'),node_to_uuid('A1')]::uuid[], array['{"type": "Member", "level": "readwrite"}', '{"type": "Pinned"}']::text[]),
        (node_to_uuid('A1'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('A1')), '{"type": "Message"}'::jsonb, null, array[]::uuid[], array[]::text[])
    $$
);



-- transitive (accessible) parents of channels
select cleanup();

select usernode('0A');
select node('01'); select pinned('0A', '01');
    select node('02', 'readwrite'); select parent('01', '02');
        select node('03', 'readwrite'); select parent('02', '03');
        select node('04', 'restricted'); select parent('02', '04');
    select node('05', 'restricted'); select parent('01', '05');

select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[node_to_uuid('01')]::uuid[], array['{"type": "Pinned"}']::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[node_to_uuid('02')]::uuid[], array['{"type": "Parent", "deletedAt": null}']::text[]),
        (node_to_uuid('02'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('02')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[node_to_uuid('03')]::uuid[], array['{"type": "Parent", "deletedAt": null}']::text[]),
        (node_to_uuid('03'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('03')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[]::uuid[], array[]::text[])
    $$
);


-- single unpinned but membership and invite of node and user
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select parent('B1', 'A1');
select node('C1', NULL); select parent('C1', 'B1');
select node('D1', NULL); select parent('D1', 'A1'); select parent('D1', 'C1');

select usernode('A2');
select member('A2', 'A1', 'readwrite');
select invite('A2', 'A1');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'), jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[node_to_uuid('A1'), node_to_uuid('A1')]::uuid[], array['{"type": "Member", "level": "readwrite"}', '{"type": "Invite"}']::text[]),
        (node_to_uuid('A1'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('A1')), '{"type": "Message"}'::jsonb, null, array[]::uuid[], array[]::text[])
    $$
);


-- single unpinned and restricted membership of node and user
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select parent('B1', 'A1');
select node('C1', NULL); select parent('C1', 'B1');
select node('D1', NULL); select parent('D1', 'A1'); select parent('D1', 'C1');

select usernode('A2');
select member('A2', 'A1', 'restricted');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'), jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[]::uuid[], array[]::text[])
    $$
);

-- transitive (accessible) parents of invitations
select cleanup();

select usernode('0A');
select node('01'); select member('0A', '01'); select invite('0A', '01');
    select node('02', 'readwrite'); select parent('01', '02');
        select node('03', 'readwrite'); select parent('02', '03');
        select node('04', 'restricted'); select parent('02', '04');
    select node('05', 'restricted'); select parent('01', '05');

select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[node_to_uuid('01'), node_to_uuid('01')]::uuid[], array['{"type": "Member", "level": "readwrite"}', '{"type": "Invite"}']::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[node_to_uuid('02')]::uuid[], array['{"type": "Parent", "deletedAt": null}']::text[]),
        (node_to_uuid('02'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('02')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[node_to_uuid('03')]::uuid[], array['{"type": "Parent", "deletedAt": null}']::text[]),
        (node_to_uuid('03'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('03')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[]::uuid[], array[]::text[])
    $$
);



-- only return specific induced edges incident to user
select cleanup();

select usernode('0A');
select usernode('0B');
select node('01');

select author(  '0B', '01'); -- will be induced
select assigned(  '0B', '01'); -- will be induced
select expanded('0B', '01');
select member(  '0B', '01'); -- will be induced
select pinned(  '0B', '01');
select parent(user_to_uuid('0B'), node_to_uuid('01')); -- will be induced

select notify(  '01', '0B'); -- will be induced
select parent(node_to_uuid('01'), user_to_uuid('0B'));

select set_eq(
    $$ select nodeid, data, role, accesslevel, targetids, array_sort(edgeData) || array[]::text[] from
            graph_page( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'),
            jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0),
            '{"type": "Message"}'::jsonb,
            'restricted'::accesslevel,
            array[]::uuid[],
            array[]::text[]
        ),
        (user_to_uuid('0B'),
            jsonb_build_object('type', 'User', 'name', '0B', 'isImplicit', false, 'revision', 0),
            '{"type": "Message"}'::jsonb,
            'restricted'::accesslevel,
            array[node_to_uuid('01'),node_to_uuid('01'),node_to_uuid('01'),node_to_uuid('01')]::uuid[],
            array_sort(array['{"type": "Member", "level": "readwrite"}','{"type": "Author"}', '{"type": "Parent", "deletedAt": null}', '{"type": "Assigned"}']::text[])
        ),
        (node_to_uuid('01'),
            jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')),
            '{"type": "Message"}'::jsonb,
            'readwrite'::accesslevel,
            array[user_to_uuid('0B')]::uuid[],
            array['{"type": "Notify"}']::text[]
        )
    $$
);




-- children of current user
select cleanup();
select usernode('0A');
select member('0A', user_to_uuid('0A'));
select node('11', 'readwrite'); select parent(node_to_uuid('11'), user_to_uuid('0A'));

select set_eq(
    $$ select * from
            graph_page( array[ user_to_uuid('0A') ]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, array[user_to_uuid('0A')]::uuid[], array['{"type": "Member", "level": "readwrite"}']::text[]),
        (node_to_uuid('11'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('11')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, array[user_to_uuid('0A')]::uuid[], array['{"type": "Parent", "deletedAt": null}']::text[])
    $$
);



-- deleted nodes
-- time interval




-- test case X
-- cleanup();
-- select usernode('A1');
-- select node('B1', 'restricted');
-- select node('B2', 'restricted');
-- select parent('B1', 'B2');
-- select member('A1', 'B1', 'readwrite');
-- select insert_before('B1', 'P1', 'N1');
-- select notify('B1', 'A1');


SELECT * FROM finish();
ROLLBACK;
