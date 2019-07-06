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


CREATE or replace FUNCTION member(nodeid varchar(2), userid varchar(2), level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Member', 'level', level), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION member(nodeid uuid, userid varchar(2), level accesslevel default 'readwrite') RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( nodeid, jsonb_build_object('type', 'Member', 'level', level), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION invite(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Invite'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION author(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Author'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION expanded(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Expanded'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION child(parentid varchar(2), childid varchar(2), deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(parentid), jsonb_build_object('type', 'Child', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), node_to_uuid(childid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION child(parentid uuid, childid uuid, deletedAt timestamp default null) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( parentid, jsonb_build_object('type', 'Child', 'deletedAt', (EXTRACT(EPOCH FROM deletedAt) * 1000)::bigint), childid )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION notify(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Notify'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION pinned(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES ( node_to_uuid(nodeid), jsonb_build_object('type', 'Pinned'), user_to_uuid(userid) )
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;

CREATE or replace FUNCTION assigned(nodeid varchar(2), userid varchar(2)) RETURNS void AS $$
begin
    INSERT INTO edge (sourceid, data, targetid)
        VALUES (node_to_uuid(nodeid), jsonb_build_object('type', 'Assigned'), user_to_uuid(userid))
        ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data;
end
$$ language plpgsql;


CREATE or replace FUNCTION cleanup() RETURNS void AS $$
begin
    truncate node cascade;

    drop table if exists can_access_cache;

    create temporary table can_access_cache (id uuid NOT NULL, can_access accesslevel_tmp) on commit drop;
    create unique index on can_access_cache (id);
end
$$ language plpgsql;


-- for comparing arrays element-wise
CREATE or replace FUNCTION array_sort(anyarray) RETURNS anyarray AS $$
    SELECT array_agg(x order by x) FROM unnest($1) x;
$$ LANGUAGE 'sql';







-- 1) request empty page
select cleanup();
select usernode('0A');
select is_empty( $$ select * from graph_traversed_page_nodes( array[]::uuid[], user_to_uuid('0A')) $$);


-- 2) non existing node
select cleanup();
select usernode('0A');
select is_empty( $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);

-- 3) single node
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- 4) two direct children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select node('12', 'readwrite'); select child('01', '12');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 5) children of current user
select cleanup();
select usernode('0A');
select member(user_to_uuid('0A'), '0A');
select node('11', 'readwrite'); select child(user_to_uuid('0A'), node_to_uuid('11'));
select node('12', 'readwrite'); select child(user_to_uuid('0A'), node_to_uuid('12'));
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[user_to_uuid('0A')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (user_to_uuid('0A')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 6) one transitive child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select node('12', 'readwrite'); select child('11', '12');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 7) two direct childs
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01');
select node('12', 'readwrite'); select child('12', '01');
select member('12', '0A', 'readwrite');
select member('11', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 8) one transitive child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01');
select node('12', 'readwrite'); select child('12', '11');
select member('12', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 9) multiple page_childs / multiple traversal starts
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01');
select member('11', '0A', 'readwrite');
select node('21', 'readwrite'); select child('01', '21');
select node('02', 'readwrite');
select node('12', 'readwrite'); select child('12', '02');
select member('12', '0A', 'readwrite');
select node('22', 'readwrite'); select child('02', '22');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01'),node_to_uuid('02')]::uuid[], user_to_uuid('0A')) $$,
    $$ values
    (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('21')),
    (node_to_uuid('02')), (node_to_uuid('12')), (node_to_uuid('22')) $$);


-- 10) direct childs of children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select member('01', '0A', 'readwrite');
select node('12', 'readwrite'); select child('11', '12');
select node('02', 'readwrite'); select child('02', '12');
select node('03', 'readwrite'); select child('03', '02');
select member('03', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- 11) diamond in children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select node('12', 'readwrite'); select child('01', '12');
select node('02', 'readwrite'); select child('11', '02'); select child('12', '02');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- 12) diamond in childs
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01');
select node('12', 'readwrite'); select child('12', '01');
select node('02', 'readwrite'); select child('02', '11'); select child('02', '12');
select member('02', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')), (node_to_uuid('02')) $$);

-- 13) self loop on page_childs
select cleanup();
select usernode('0A');
select node('01', 'readwrite'); select child('01', '01');
select member('01', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- 14) self loop on child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11'); select child('11', '11');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- 15) self loop on child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01'); select child('11', '11');
select member('11', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- 16) page_childs in cycle
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11'); select child('11', '01');
select member('11', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')) $$);

-- 17) cycle in children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select node('12', 'readwrite'); select child('11', '12'); select child('12', '11');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 18) cycle in childs
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select node('11', 'readwrite'); select child('11', '01');
select node('12', 'readwrite'); select child('11', '12'); select child('12', '11');
select member('11', '0A', 'readwrite');
select member('12', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);




-- access rights

-- the following 8 tests are basically the same as in 'simple_inheritance' of can_access_node
select cleanup();

-- 19) case 1:
select usernode('A1'); -- user
select node('B1', 'restricted'); -- node with permission
select node('C1', NULL); -- node with permission inheritance
select child('B1', 'C1'); -- inheritance happens via this child edge
select member('B1', 'A1', 'restricted'); -- membership with level

select is_empty($$ select * from graph_traversed_page_nodes(array[node_to_uuid('B1')], user_to_uuid('A1')) $$);

-- 20) case 2:
select usernode('A2');
select node('B2', 'readwrite');
select node('C2', null);
select child('B2', 'C2');
select member('B2', 'A2', 'restricted');

select is_empty($$ select * from graph_traversed_page_nodes(array[node_to_uuid('B2')], user_to_uuid('A2')) $$);

-- 21) case 3:
select usernode('A3');
select node('B3', null);
select node('C3', null);
select child('B3', 'C3');
select member('B3', 'A3', 'restricted');

select is_empty($$ select * from graph_traversed_page_nodes(array[node_to_uuid('B3')], user_to_uuid('A3')) $$);


-- 22) case 4:
select usernode('A4');
select node('B4', 'restricted');
select node('C4', null);
select child('B4', 'C4');
select member('B4', 'A4', 'readwrite');

select set_eq(
    $$ select * from graph_traversed_page_nodes(array[node_to_uuid('B4')], user_to_uuid('A4')) $$,
    $$ values (node_to_uuid('B4')), (node_to_uuid('C4')) $$
);

-- 23) case 5:
select usernode('A5');
select node('B5', 'readwrite');
select node('C5', null);
select child('B5', 'C5');
select member('B5', 'A5', 'readwrite');

select set_eq(
    $$ select * from graph_traversed_page_nodes(array[node_to_uuid('B5')], user_to_uuid('A5')) $$,
    $$ values (node_to_uuid('C5')), (node_to_uuid('B5')) $$
);


-- 24) case 6:
select usernode('A6');
select node('B6', null);
select node('C6', null);
select child('B6', 'C6');
select member('B6', 'A6', 'readwrite');

select set_eq(
    $$ select * from graph_traversed_page_nodes(array[node_to_uuid('B6')], user_to_uuid('A6')) $$,
    $$ values (node_to_uuid('B6')), (node_to_uuid('C6')) $$
);



-- 25) case 7:
select usernode('A7');
select node('B7', 'restricted');
select node('C7', null);
select child('B7', 'C7');

select is_empty($$ select * from graph_traversed_page_nodes(array[node_to_uuid('B7')], user_to_uuid('A7')) $$);

-- 26) case 8:
select usernode('A8');
select node('B8', 'readwrite');
select member('B8', 'A8', 'readwrite');
select node('C8', null);
select child('B8', 'C8');

select set_eq(
    $$ select * from graph_traversed_page_nodes(array[node_to_uuid('B8')], user_to_uuid('A8')) $$,
    $$ values (node_to_uuid('B8')), (node_to_uuid('C8')) $$
);



-- 27) single, inaccessible node
select cleanup();
select usernode('0A');
select node('01', NULL); -- access: inherited, but no childs
select is_empty($$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);

-- 28) inaccessible children
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'restricted'); select child('01', '11');
select node('12', 'readwrite'); select child('11', '12');
select member('12', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$);

-- 29) inaccessible childs
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'restricted'); select child('11', '01');
select node('12', 'readwrite'); select child('12', '11');
select member('12', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')) $$,
    'inaccessible childs');

-- 30) inaccessible direct child of child
select cleanup();
select usernode('0A');
select node('01', 'readwrite');
select member('01', '0A', 'readwrite');
select node('11', 'readwrite'); select child('01', '11');
select member('11', '0A', 'readwrite');
select node('12', 'readwrite'); select child('11', '12');
select member('12', '0A', 'readwrite');
select node('02', 'restricted'); select child('02', '12');
select node('03', 'readwrite'); select child('03', '02');
select member('03', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')), (node_to_uuid('11')), (node_to_uuid('12')) $$);

-- 31) accessible accesslevel of child is determined by child
select cleanup();
select usernode('0A');
select node('01', null); -- inherited
select node('11', null); select child('01', '11');
select node('12', 'readwrite'); select child('12', '01');
select member('12', '0A', 'readwrite');
select set_eq(
    $$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$,
    $$ values (node_to_uuid('01')),(node_to_uuid('11')),(node_to_uuid('12')) $$);

-- 32) inaccessible accesslevel of child is determined by child
select cleanup();
select usernode('0A');
select node('01', null); -- inherited
select node('11', null); select child('01', '11');
select node('12', 'restricted'); select child('12', '01');
select is_empty($$ select * from graph_traversed_page_nodes( array[node_to_uuid('01')]::uuid[], user_to_uuid('0A')) $$);



SELECT * FROM finish();
ROLLBACK;
