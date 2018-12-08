BEGIN;
SELECT plan(32);

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







-- request empty page
select cleanup();
select usernode('0A');
select is_empty( $$ select * from graph_page_nodes( array[]::uuid[], user_to_uuid('0A')) $$);


-- non existing node
select cleanup();
select usernode('0A');
select is_empty( $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);

-- single node
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- two direct children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '01');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- children of current user
select cleanup();
select usernode('0A');
select member('0A', user_to_uuid('0A'));
select node('11', 'readwrite'); select parent(node_to_uuid('11'), user_to_uuid('0A'));
select node('12', 'readwrite'); select parent(node_to_uuid('12'), user_to_uuid('0A'));
select set_eq(
    $$ select * from graph_page_nodes( array[user_to_uuid('0A')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (user_to_uuid('0A')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- one transitive child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '11');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- two direct parents
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11');
select node('12', 'readwrite'); select parent('01', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- one transitive parent
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11');
select node('12', 'readwrite'); select parent('11', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- multiple page_parents / multiple traversal starts
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11');
select node('21', 'readwrite'); select parent('21', '01');
select node('02', 'readwrite');
select node('12', 'readwrite'); select parent('02', '12');
select node('22', 'readwrite'); select parent('22', '02');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01'),node_to_uuid('02')]::uuid[], user_to_uuid('0A')) $$,
    $$ values
    (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('21')),
    (node_to_uuid('02')), (node_to_uuid('12')), (node_to_uuid('22')) $$);


-- direct parents of children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '11');
select node('02', 'readwrite'); select parent('12', '02');
select node('03', 'readwrite'); select parent('02', '03');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- diamond in children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '01');
select node('02', 'readwrite'); select parent('02', '11'); select parent('02', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- diamond in parents
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11');
select node('12', 'readwrite'); select parent('01', '12');
select node('02', 'readwrite'); select parent('11', '02'); select parent('12', '02');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- self loop on page_parent
select cleanup();
select usernode('0A');
select node('01', 'readwrite'); select parent('01', '01');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- self loop on child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01'); select parent('11', '11');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- self loop on parent
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11'); select parent('11', '11');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- page_parent in cycle
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01'); select parent('01', '11');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- cycle in children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '11'); select parent('11', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- cycle in parents
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('01', '11');
select node('12', 'readwrite'); select parent('12', '11'); select parent('11', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);




-- access rights

-- the following 8 tests are basically the same as in 'simple_inheritance' of can_access_node
select cleanup();

-- case 1:
select usernode('A1'); -- user
select node('B1', 'restricted'); -- node with permission
select node('C1', NULL); -- node with permission inheritance
select parent('C1', 'B1'); -- inheritance happens via this parent edge
select member('A1', 'B1', 'restricted'); -- membership with level

select is_empty($$ select * from graph_page_nodes(array[node_to_uuid('B1')], user_to_uuid('A1')) $$);

-- case 2:
select usernode('A2');
select node('B2', 'readwrite');
select node('C2', null);
select parent('C2', 'B2');
select member('A2', 'B2', 'restricted');

select is_empty($$ select * from graph_page_nodes(array[node_to_uuid('B2')], user_to_uuid('A2')) $$);

-- case 3:
select usernode('A3');
select node('B3', null);
select node('C3', null);
select parent('C3', 'B3');
select member('A3', 'B3', 'restricted');

select is_empty($$ select * from graph_page_nodes(array[node_to_uuid('B3')], user_to_uuid('A3')) $$);


-- case 4:
select usernode('A4');
select node('B4', 'restricted');
select node('C4', null);
select parent('C4', 'B4');
select member('A4', 'B4', 'readwrite');

select set_eq(
    $$ select * from graph_page_nodes(array[node_to_uuid('B4')], user_to_uuid('A4')) $$,
    $$ values (node_to_uuid('B4')), (node_to_uuid('C4')) $$
);

-- case 5:
select usernode('A5');
select node('B5', 'readwrite');
select node('C5', null);
select parent('C5', 'B5');
select member('A5', 'B5', 'readwrite');

select set_eq(
    $$ select * from graph_page_nodes(array[node_to_uuid('B5')], user_to_uuid('A5')) $$,
    $$ values (node_to_uuid('C5')), (node_to_uuid('B5')) $$
);


-- case 6:
select usernode('A6');
select node('B6', null);
select node('C6', null);
select parent('C6', 'B6');
select member('A6', 'B6', 'readwrite');

select set_eq(
    $$ select * from graph_page_nodes(array[node_to_uuid('B6')], user_to_uuid('A6')) $$,
    $$ values (node_to_uuid('B6')), (node_to_uuid('C6')) $$
);



-- case 7:
select usernode('A7');
select node('B7', 'restricted');
select node('C7', null);
select parent('C7', 'B7');

select is_empty($$ select * from graph_page_nodes(array[node_to_uuid('B7')], user_to_uuid('A7')) $$);

-- case 8:
select usernode('A8');
select node('B8', 'readwrite');
select node('C8', null);
select parent('C8', 'B8');

select set_eq(
    $$ select * from graph_page_nodes(array[node_to_uuid('B8')], user_to_uuid('A8')) $$,
    $$ values (node_to_uuid('B8')), (node_to_uuid('C8')) $$
);



-- single, inaccessible node
select cleanup();
select usernode('0A');
select node('01', NULL); -- access: inherited, but no parents
select is_empty($$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);

-- inaccessible children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'restricted'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '11');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- inaccessible parents
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'restricted'); select parent('01', '11');
select node('12', 'readwrite'); select parent('11', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$,
    'inaccessible parents');

-- inaccessible direct parent of child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select parent('11', '01');
select node('12', 'readwrite'); select parent('12', '11');
select node('02', 'restricted'); select parent('12', '02');
select node('03', 'readwrite'); select parent('02', '03');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- accessible accesslevel of child is determined by parent
select cleanup();
select usernode('0A');
select node('01', null); -- inherited
select node('11', null); select parent('11', '01');
select node('12', 'readwrite'); select parent('01', '12');
select set_eq(
    $$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')),(node_to_uuid('11')),(node_to_uuid('12')) $$);

-- inaccessible accesslevel of child is determined by parent
select cleanup();
select usernode('0A');
select node('01', null); -- inherited
select node('11', null); select parent('11', '01');
select node('12', 'restricted'); select parent('01', '12');
select is_empty($$ select * from graph_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);



SELECT * FROM finish();
ROLLBACK;
