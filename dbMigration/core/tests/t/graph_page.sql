BEGIN;
SELECT plan(8);
-- SELECT plan(5);

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


CREATE or replace FUNCTION member(nodeid varchar(2), userid varchar(2), level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Member', 'level', level), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION member(nodeid uuid, userid varchar(2), level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( nodeid, jsonb_build_object('type', 'Member', 'level', level), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION invite(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Invite'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION author(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Author'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION expanded(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Expanded'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION child(parentid varchar(2), childid varchar(2), deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(parentid), jsonb_build_object('type', 'Child', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), node_to_uuid(childid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION child(parentid uuid, childid uuid, deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( parentid, jsonb_build_object('type', 'Child', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), childid )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION notify(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Notify'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION pinned(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Pinned'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE SET data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION assigned(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Assigned'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE not(multiedge((data->>'type')::text)) DO UPDATE set data = EXCLUDED.data;
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

-- case 1: single user and node
select node('01');
select usernode('0A');
select member('01', '0A');

select set_eq(
    $$ select * from
            graph_page( array[ node_to_uuid('01') ]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[]::uuid[], array[]::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[],
            array[user_to_uuid('0A')]::uuid[], array['{"type": "Member", "level": "readwrite"}']::text[])
    $$
);


-- case 2: single user and node with children
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select child('A1', 'B1');
select node('C1', NULL); select child('B1', 'C1');
select node('D1', NULL); select child('A1', 'D1'); select child('C1', 'D1');

select usernode('A2');
select member('A1', 'A2', 'readwrite');
select pinned('A1', 'A2');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'), jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[]::uuid[], array[]::text[]),
        (node_to_uuid('A1'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('A1')), '{"type": "Message"}'::jsonb, null, null::jsonb[],
            array[node_to_uuid('B1'),node_to_uuid('D1'),user_to_uuid('A2'),user_to_uuid('A2')]::uuid[], array['{"type": "Child", "deletedAt": null}', '{"type": "Child", "deletedAt": null}', '{"type": "Member", "level": "readwrite"}', '{"type": "Pinned"}']::text[])
    $$
);



-- case 3: transitive (accessible) child of channels
select cleanup();

select usernode('0A');
select node('01'); select pinned('01', '0A');
    select node('02', 'readwrite'); select child('02', '01');
        select node('03', 'readwrite'); select child('03', '02');
        select node('04', 'restricted'); select child('04', '02');
    select node('05', 'restricted'); select child('05', '01');

select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[]::uuid[], array[]::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[user_to_uuid('0A')]::uuid[], array['{"type": "Pinned"}']::text[]),
        (node_to_uuid('02'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('02')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[node_to_uuid('01')]::uuid[], array['{"type": "Child", "deletedAt": null}']::text[]),
        (node_to_uuid('03'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('03')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[node_to_uuid('02')]::uuid[], array['{"type": "Child", "deletedAt": null}']::text[])
    $$
);


-- case 4: single unpinned but membership and invite of node and user
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select child('A1', 'B1');
select node('C1', NULL); select child('B1', 'C1');
select node('D1', NULL); select child('A1', 'D1'); select child('C1', 'D1');

select usernode('A2');
select member('A1', 'A2', 'readwrite');
select invite('A1', 'A2');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'), jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[]::uuid[], array[]::text[]),
        (node_to_uuid('A1'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('A1')), '{"type": "Message"}'::jsonb, null, null::jsonb[], array[node_to_uuid('B1'), node_to_uuid('D1'), user_to_uuid('A2'), user_to_uuid('A2')]::uuid[], array['{"type": "Child", "deletedAt": null}','{"type": "Child", "deletedAt": null}', '{"type": "Invite"}', '{"type": "Member", "level": "readwrite"}']::text[])
    $$
);


-- case 5: single unpinned and restricted membership of node and user
select cleanup();

select node('A1', NULL);
select node('B1', NULL); select child('A1', 'B1');
select node('C1', NULL); select child('B1', 'C1');
select node('D1', NULL); select child('A1', 'D1'); select child('C1', 'D1');

select usernode('A2');
select member('A1', 'A2', 'restricted');


select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('A2'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('A2'),
            jsonb_build_object('type', 'User', 'name', 'A2', 'isImplicit', false, 'revision', 0),
            '{"type": "Message"}'::jsonb,
            'restricted'::accesslevel,
            null::jsonb[],
            array[]::uuid[],
            array[]::text[]
        )
    $$
);

-- case 6: transitive (accessible) child of invitations
select cleanup();

select usernode('0A');
select node('01'); select member('01', '0A'); select invite('01', '0A');
    select node('02', 'readwrite'); select child('02', '01');
        select node('03', 'readwrite'); select child('03', '02');
        select node('04', 'restricted'); select child('04', '02');
    select node('05', 'restricted'); select child('05', '01');

select set_eq(
    $$ select * from
            graph_page( array[]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[]::uuid[], array[]::text[]),
        (node_to_uuid('01'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[user_to_uuid('0A'), user_to_uuid('0A')]::uuid[], array['{"type": "Invite"}', '{"type": "Member", "level": "readwrite"}']::text[]),
        (node_to_uuid('02'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('02')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[node_to_uuid('01')]::uuid[], array['{"type": "Child", "deletedAt": null}']::text[]),
        (node_to_uuid('03'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('03')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[node_to_uuid('02')]::uuid[], array['{"type": "Child", "deletedAt": null}']::text[])
    $$
);



-- case 7: only return specific induced edges incident to user
select cleanup();

select usernode('0A');
select usernode('0B');
select node('01');

select assigned('01', '0B'); -- will be induced
select author(  '01', '0B'); -- will be induced
select member(  '01', '0B'); -- will be induced

select expanded('01', '0B'); -- will be induced
select notify(  '01', '0B'); -- will be induced
select pinned(  '01', '0B'); -- will be induced

select child(node_to_uuid('01'), user_to_uuid('0B')); -- will be induced

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
        (node_to_uuid('01'),
            jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('01')),
            '{"type": "Message"}'::jsonb,
            'readwrite'::accesslevel,
            array[user_to_uuid('0B'),user_to_uuid('0B'),user_to_uuid('0B'),user_to_uuid('0B'),user_to_uuid('0B'),user_to_uuid('0B'),user_to_uuid('0B')]::uuid[],
            array_sort(array['{"type": "Assigned"}','{"type": "Author"}','{"type": "Child", "deletedAt": null}','{"type": "Expanded"}','{"type": "Member", "level": "readwrite"}','{"type": "Notify"}','{"type": "Pinned"}']::text[])
        )
        -- , (user_to_uuid('0B'),
        --     jsonb_build_object('type', 'User', 'name', '0B', 'isImplicit', false, 'revision', 0),
        --     '{"type": "Message"}'::jsonb,
        --     'restricted'::accesslevel,
        --     array[]::uuid[],
        --     array[]::text[]
        -- )
    $$
);




-- case 8: children of current user
select cleanup();
select usernode('0A');
select member(user_to_uuid('0A'), '0A');
select node('11', 'readwrite');
select child(node_to_uuid('11'), user_to_uuid('0A'));

select set_eq(
    $$ select * from
            graph_page( array[ user_to_uuid('0A') ]::uuid[], user_to_uuid('0A'))
    $$,
    -- table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
    $$ values
        (user_to_uuid('0A'), jsonb_build_object('type', 'User', 'name', '0A', 'isImplicit', false, 'revision', 0), '{"type": "Message"}'::jsonb, 'restricted'::accesslevel, null::jsonb[], array[user_to_uuid('0A')]::uuid[], array['{"type": "Member", "level": "readwrite"}']::text[]),
        (node_to_uuid('11'), jsonb_build_object('type', 'PlainText', 'content', node_to_uuid('11')), '{"type": "Message"}'::jsonb, 'readwrite'::accesslevel, null::jsonb[], array[user_to_uuid('0A')]::uuid[], array['{"type": "Child", "deletedAt": null}']::text[])
    $$
);



-- deleted nodes
-- time interval




-- test case X
-- cleanup();
-- select usernode('A1');
-- select node('B1', 'restricted');
-- select node('B2', 'restricted');
-- select child('B1', 'B2');
-- select member('A1', 'B1', 'readwrite');
-- select insert_before('B1', 'P1', 'N1');
-- select notify('B1', 'A1');


SELECT * FROM finish();
ROLLBACK;
