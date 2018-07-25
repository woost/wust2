--------------------
--- procedures
--------------------

-- drop
drop function create_traversal_function;
drop function graph_page;
drop function graph_page_nodes;
drop function readable_graph_page_nodes_with_channels;
drop function readable_graph_page_nodes_with_channels_with_orphans;
drop function graph_page_with_orphans;
drop function induced_subgraph;
drop function mergeFirstUserIntoSecond;
drop function notified_users;
drop function now_utc;
drop function traverse_children;
drop function traverse_parents;
drop function can_access_node_recursive;
drop function can_access_node;
drop function can_access_node_in_down_traversal;
drop function inaccessible_nodes;

drop aggregate array_merge_agg(anyarray);
drop function array_intersect;
drop function array_merge;

-- recursively check whether a node is accessible.
-- non-existing user: we assume that a user exist in any cases and therefore we do not handle this explicitly
create function can_access_node_recursive(userid uuid, nodeid uuid, visited uuid[] default array[]::uuid[]) returns boolean as $$
declare
    node_access_level accesslevel;
    result boolean;
begin
    IF ( nodeid = any(visited) ) THEN return false; end if; -- prevent inheritance cycles

    -- is there a membership?
    select data->>'level' into node_access_level from edge where data->>'type' = 'Member' and edge.sourceid = userid and edge.targetid = nodeid limit 1;
    IF (node_access_level IS NULL) THEN -- if no member edge exists
        -- read access level directly from node
        select accessLevel into node_access_level from node where id = nodeid limit 1;
        IF (node_access_level IS NULL) THEN -- null means inherit for the node
            -- recursively inherit permissions from parents. minimum one parent needs to allow access.
            select bool_or(can_access_node_recursive(userid, edge.targetid, visited || nodeid)) into result from edge where data->>'type' = 'Parent' and edge.sourceid = nodeid;
            return COALESCE(result, false); -- if there are no parents => no permission
        END IF;
    END IF;

    return COALESCE(node_access_level = 'readwrite', false); -- if no access level was found => no permission
end;
$$ language plpgsql STABLE;

create function can_access_node(userid uuid, nodeid uuid) returns boolean as $$
begin
    IF NOT EXISTS (select 1 from node where id = nodeid) then return true; end if; -- everybody has full access to non-existant nodes
    return can_access_node_recursive(userid, nodeid);
end;
$$ language plpgsql STABLE;

-- check acessibility without recursion. can be used when check permissions in
-- a down traversal that already checks permissions.
create function can_access_node_in_down_traversal(userid uuid, nodeid uuid) returns boolean as $$
declare
    node_access_level accesslevel;
    result boolean;
begin
    -- is there a membership?
    select data->>'level' into node_access_level from edge where data->>'type' = 'Member' and edge.sourceid = userid and edge.targetid = nodeid limit 1;
    IF (node_access_level IS NULL) THEN -- if no member edge exists
        -- read access level directly from node
        select accessLevel into node_access_level from node where id = nodeid limit 1;
        IF (node_access_level IS NULL) THEN -- null means inherit for the node
            -- we are in a readable page and therefore in an accessible subtree => allow
            return true;
        END IF;
    END IF;

    return COALESCE(node_access_level = 'readwrite', false); -- if no access level was found => no permission
end;
$$ language plpgsql STABLE;

-- returns nodeids which the user does not have permission for
create function inaccessible_nodes(userid uuid, nodeids uuid[]) returns uuid[] as $$
    select COALESCE(array_agg(id), array[]::uuid[]) from (select unnest(nodeids) id) ids where not can_access_node(userid, id);
$$ language sql stable;

-- IMPLEMENTATIONS
CREATE FUNCTION array_intersect(anyarray, anyarray)
  RETURNS anyarray
  language sql
as $FUNCTION$
    SELECT ARRAY(
        SELECT UNNEST($1)
        INTERSECT
        SELECT UNNEST($2)
    );
$FUNCTION$;




create function public.array_merge(arr1 anyarray, arr2 anyarray)
    returns anyarray language sql immutable
as $$
    -- select array_agg(distinct elem order by elem)
    select array_agg(distinct elem)
    from (
        select unnest(arr1) elem
        union
        select unnest(arr2)
    ) s
$$;




create aggregate array_merge_agg(anyarray) (
    sfunc = array_merge,
    stype = anyarray
);



-- this function generates traversal functions.
-- traversal over specified edges. Returns array of ids. Expects a temporary table 'visited (id uuid NOT NULL)' to exist.
-- can_access_node_contextual has to be a function that accepts a userid and a nodeid and returns boolean.
-- for downwards traversal, we use a more optimized version which can assume that parent nodes are accessible.
-- for upwards traversal, we use the normal can_access_node which has to check upwards if rights are inherited.
create function create_traversal_function(name text, edge regclass, edge_source text, parentlabel text, edge_target text, node_limit int, can_access_node_contextual text) returns void as $$
begin
    -- TODO: benchmark array_length vs cardinality
    -- TODO: test if traversal only happens via provided label
EXECUTE '
create function ' || quote_ident(name) || '(start uuid[], stop uuid[], userid uuid) returns uuid[] as $func$
declare
    queue uuid[] := start;
    can_access_start boolean;
begin
    select bool_and(can_access_node(userid, startnodes.id)) into can_access_start from (select unnest(start) as id) as startnodes;
    IF (not (COALESCE(can_access_start, false))) THEN
        return array[]::uuid[];
    END IF;

    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(select unnest(queue) except select unnest(stop)); -- stop traversal for ids in stop-array
        IF (select count(*) from visited) > '|| node_limit ||' THEN -- node limit reached, stop traversal
            queue := ARRAY[];
        ELSE
            queue := array(
                select distinct '|| quote_ident(edge_target) ||'
                from (select unnest(queue) as id) as q
                join '|| edge ||' on '|| quote_ident(edge_source) ||' = q.id and '|| edge ||'.data->>''type'' = '''|| parentlabel ||''' and ' || quote_ident(can_access_node_contextual) || '(userid, ' || quote_ident(edge_target) || ')
                left outer join visited on '|| quote_ident(edge_target) ||' = visited.id
                where visited.id is NULL
                limit '|| node_limit ||' -- also apply limit on node degree
            );
        END IF;
    END LOOP;
    return array (select id from visited);
end;
$func$ language plpgsql;
';
end
$$ language plpgsql; -- not stable because of temporary visited table






select create_traversal_function('traverse_children', 'edge', 'targetid', 'Parent', 'sourceid', 10000, 'can_access_node_in_down_traversal');

select create_traversal_function('traverse_parents', 'edge', 'sourceid', 'Parent', 'targetid', 10000, 'can_access_node');






-- traverses from parents and stops at specified children. returns visited node-ids (including parents and children)
create function graph_page_nodes(edge_parents uuid[], edge_children uuid[], userid uuid) returns uuid[] as $$
declare
    parents uuid[];
    children uuid[];
begin
    -- we have to privde the temporary visited table for the traversal functions,
    -- since it is not possible to create function local temporary tables.
    -- Creating is transaction local, which leads to "table 'visited' already exists" errors,
    -- when created inside functions.
    create temporary table visited (id uuid NOT NULL) on commit drop;
    create unique index on visited (id);

    -- each traversal gets a prefilled set of visited vertices ( the ones where the traversal should stop )

    -- walk from edge_parents in source direction until hitting edge_children (includes both edge_parents and edge_children)
    children := traverse_children(edge_parents, edge_children, userid); -- start down traversal at parents, traversal stops at children
    truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges

    parents := traverse_parents(edge_parents, array[]::uuid[], userid); -- start up traversal at parents


    return array( select distinct unnest(children || parents) ); -- return distinct nodeids
end;
$$ language plpgsql;


create function induced_subgraph(nodeids uuid[])
returns table(nodeid uuid, data jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
as $$
    select
    node.id, node.data, node.accesslevel, -- all node columns
    array_remove(array_agg(edge.targetid), NULL), array_remove(array_agg(edge.data::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from node
    left outer join edge on edge.sourceid = node.id AND edge.targetid = ANY(nodeids)
    where node.id = ANY(nodeids)
    group by (node.id, node.data, node.accesslevel) -- needed for multiple outgoing edges
$$ language sql;


create function readable_graph_page_nodes_with_channels(parents uuid[], children uuid[], userid uuid)
returns table(nodeid uuid)
as $$
    with cpid as (select (data->>'channelNodeId')::uuid as channelnodeid from node where id = userid and data->>'type' = 'User')
        select cpid.channelnodeid as nodeid from cpid  -- channel post of user, containing all user channels
        union
        select sourceid as nodeid from edge,cpid where edge.targetid = cpid.channelnodeid and data->>'type' = 'Parent' -- all user channels (children of channelnodeid)
        union
        select unnest(graph_page_nodes(parents, children, userid)) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
$$ language sql;


create function readable_graph_page_nodes_with_channels_with_orphans(parents uuid[], children uuid[], userid uuid)
returns table(nodeid uuid)
as $$
    with cpid as (select (data->>'channelNodeId')::uuid as channelnodeid from node where id = userid and data->>'type' = 'User')
        select cpid.channelnodeid as nodeid from cpid  -- channel post of user, containing all user channels
        union
        select sourceid as nodeid from edge,cpid where edge.targetid = cpid.channelnodeid and data->>'type' = 'Parent' -- all user channels (children of channelnodeid)
        union
        select unnest(
                graph_page_nodes(parents, children, userid) ||
                array(select id from node where id not in (select sourceid from edge where data->>'type' = 'Parent')) -- orphans
        ) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
$$ language sql;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], children uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with
            nodes as (select readable_graph_page_nodes_with_channels(parents, children, userid) as nodeid),
            users as (select edge.sourceid as userid from edge, nodes where edge.targetid = nodes.nodeid and (edge.data->>'type' = 'Author' or edge.data->>'type' = 'Member')) -- all available authors and members for all nodes
        select nodeid from nodes
        union
        select userid from users
    )
);
$$ language sql;


-- page(parents, children) -> graph as adjacency list
create function graph_page_with_orphans(parents uuid[], children uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with
            nodes as (select readable_graph_page_nodes_with_channels_with_orphans(parents, children, userid) as nodeid),
            users as (select edge.sourceid as userid from edge, nodes where edge.targetid = nodes.nodeid and (edge.data->>'type' = 'Author' or edge.data->>'type' = 'Member')) -- all available authors and members for all nodes
        select nodeid from nodes
        union
        select userid from users
    )
);
$$ language sql;









-- this works on nodes, not only users. maybe restrict?
CREATE or replace FUNCTION mergeFirstUserIntoSecond(oldUser uuid, keepUser uuid) RETURNS VOID AS $$
 DECLARE
 tables CURSOR FOR
    select conrelid::regclass as table, a.attname as col
    from pg_attribute af, pg_attribute a,
    (select conrelid,confrelid,conkey[i] as conkey, confkey[i] as confkey
    from (select conrelid,confrelid,conkey,confkey,
                    generate_series(1,array_upper(conkey,1)) as i
            from pg_constraint where contype = 'f') ss) ss2
    where af.attnum = confkey and af.attrelid = confrelid and
        a.attnum = conkey and a.attrelid = conrelid
    AND confrelid::regclass = 'node'::regclass AND af.attname = 'id';
    oldChannelNodeId uuid;
    keepChannelNodeId uuid;
    record record;
 BEGIN

 FOR table_record IN tables LOOP
    -- insert record with updated target columns into the target table
    execute format('insert into %I (select ( json_populate_record(%I, json_build_object(''%I'', ''%s'')) ).* from %I where %I = ''%s'') on conflict do nothing', table_record.table, table_record.table, table_record.col, keepUser, table_record.table, table_record.col, oldUser);

    -- deleted the newly inserted records
    execute format('delete from %I where %I = ''%s''', table_record.table, table_record.col, oldUser);
 END LOOP;

 -- merge channel nodes
 oldChannelNodeId := (select data->>'channelNodeId' from node where id = oldUser and data->>'type' = 'User');
 keepChannelNodeId := (select data->>'channelNodeId' from node where id = keepUser and data->>'type' = 'User');
 insert into edge (select keepChannelNodeId as sourceid, edge.targetid, edge.data from edge where edge.sourceid = oldChannelNodeId) ON CONFLICT DO NOTHING;
 insert into edge (select edge.sourceid, keepChannelNodeId as targetid, edge.data from edge where edge.targetid = oldChannelNodeId) ON CONFLICT DO NOTHING;

 delete from node where id = oldUser and data->>'type' = 'User';
 delete from node where node.id = oldChannelNodeId;

 END;
$$ LANGUAGE plpgsql;






create function notified_users(start uuid[]) returns table(userid uuid, nodeids uuid[]) as $func$
declare
    queue uuid[] := start;
    nextqueue uuid[] := '{}';
    parentlabel text := 'Parent';
    -- items record;
    begin
        create temporary table visited (id uuid NOT NULL, reason uuid) on commit drop;
        create unique index on visited (id,reason);

        create temporary table reasons (id uuid NOT NULL, reasons uuid[] NOT NULL) on commit drop;
        create unique index on reasons (id);
        insert into reasons (select unnest(start), array[unnest(start)]);

        -- RAISE NOTICE 'init visited:';
        -- FOR items IN select id, reason from visited LOOP
        --     RAISE NOTICE 'v: %, r: %', items.id, items.reason;
        -- END LOOP;

        -- RAISE NOTICE 'init reasons:';
        -- FOR items IN select id, reasons from reasons LOOP
        --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
        -- END LOOP;


        WHILE array_length(queue,1) > 0 LOOP
            insert into visited (select q, unnest(reasons) from unnest(queue) q join reasons on q = reasons.id) on conflict do nothing;
            -- RAISE NOTICE 'queue: %', queue;
            -- RAISE NOTICE 'visited: %', array(select * from visited);
            -- RAISE NOTICE 'reasons: % %', array(select id from reasons), array(select reasons from reasons);

            IF (select count(*) from visited) > 10000 THEN -- node limit reached, stop traversal
                queue := ARRAY[];
            ELSE

                insert into reasons (
                    select targetid, array_merge_agg(reasons) -- custom agg function
                    from (select unnest(queue) as id) as q
                    join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(queue) as id) as q
                    join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    left outer join visited on targetid = visited.id
                    where visited.id is NULL or NOT (visited.reason = any(reasons.reasons))
                    limit 10000 -- also apply limit on node degree
                );

                -- RAISE NOTICE 'tmp queue: %', nextqueue;

                -- RAISE NOTICE 'tmp reasons:';
                -- FOR items IN select id, reasons from reasons LOOP
                --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
                -- END LOOP;

                -- TODO: why do we have to do this twice? Else there might be problems with reason propagation through cycles.
                insert into reasons (
                    select targetid, array_merge_agg(reasons) -- custom agg function
                    from (select unnest(nextqueue) as id) as q
                    join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(nextqueue) as id) as q
                    join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    left outer join visited on targetid = visited.id
                    where visited.id is NULL or NOT (visited.reason = any(reasons.reasons))
                    limit 10000 -- also apply limit on node degree
                );

                -- RAISE NOTICE 'nextqueue: %', nextqueue;

                --         raise notice 'iiiii: %', array(select targetid
                    --         from (select unnest(queue) as id) as q
                    --         join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    --         join reasons on reasons.id = q.id);
                --         raise notice 'uuuuu: %', array(select reasons.reasons
                    --         from (select unnest(queue) as id) as q
                    --         join edge on sourceid = q.id and edge.data->>'type' = parentlabel
                    --         join reasons on reasons.id = q.id);



                queue := nextqueue;
                -- RAISE NOTICE 'visited:';
                -- FOR items IN select id, reason from visited LOOP
                --     RAISE NOTICE 'v: %, r: %', items.id, items.reason;
                -- END LOOP;

                -- RAISE NOTICE 'reasons:';
                -- FOR items IN select id, reasons from reasons LOOP
                --     RAISE NOTICE 'id: %, r: %', items.id, items.reasons;
                -- END LOOP;

                -- RAISE NOTICE '';
            END IF;
    END LOOP;

    return query select edge.sourceid, array_merge_agg(reasons)
    from reasons
    join edge on reasons.id = edge.targetid where data->>'type' = 'Member'
    group by edge.sourceid;
end;
$func$ language plpgsql;


CREATE FUNCTION now_utc() RETURNS TIMESTAMP AS $$
    SELECT NOW() AT TIME ZONE 'utc';
$$ language sql STABLE;


















