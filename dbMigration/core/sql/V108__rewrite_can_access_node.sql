-- begin;

CREATE TYPE accesslevel_tmp AS ENUM ('unknown', 'restricted', 'readwrite');


------------------------------------------------------------
----- COPY & PASTE IN EVERY MIGRATION FROM HERE TO END -----
------------------------------------------------------------
---- Remember to change everything such that it is      ----
---- possible to copy & paste it for the next migration ----
------------------------------------------------------------

--------------------
--- begin procedures
--------------------
-- drop
drop function graph_page;
drop function graph_traversed_page_nodes;
drop function readable_graph_page_nodes_with_channels;
-- drop function readable_graph_page_nodes_with_channels_with_orphans;
drop function user_quickaccess_nodes;
-- drop function graph_page_with_orphans;
drop function induced_subgraph;
drop function mergeFirstUserIntoSecond;
drop function can_access_node_recursive;
drop function can_access_node;
drop function can_access_node_via_url;
drop function can_access_node_expecting_cache_table;
drop function can_access_node_providing_cache_table;
drop function inaccessible_nodes;
drop function notified_users_by_nodeid;
drop function notified_users_at_deepest_node;
drop function now_utc;
drop function millis_to_timestamp;

drop aggregate array_merge_agg(anyarray);
drop function array_intersect;
drop function array_merge;

create function millis_to_timestamp(millis anyelement) returns timestamp with time zone as $$
    select to_timestamp(millis::bigint / 1000)
$$ language sql stable;

-- recursively check whether a node is accessible.
-- non-existing user: we assume that a user exist in any cases and therefore we do not handle this explicitly
create function can_access_node_recursive(userid uuid, nodeid uuid, get_via_url_mode boolean) returns boolean as $$
declare
    current_level accesslevel_tmp;
begin
    select can_access into current_level from can_access_cache where can_access_cache.id = nodeid and can_access_cache.can_access <> 'unknown';

    if current_level = 'readwrite' then
        return true;
    elsif current_level = 'restricted' then
        return false;
    end if;


    --- 1. get all transitive_parents of nodes and write them into temp table
    insert into can_access_cache (
        with recursive
        transitive_parents(id) AS (
            select id from node where id = nodeid
            union -- discards duplicates, therefore handles cycles and diamond cases
            select contentedge.source_nodeid
            FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
            where not exists (select * from can_access_cache where can_access_cache.id = contentedge.source_nodeid) -- if node already exists in cache, don't go further

        )
        select id, 'unknown', false from transitive_parents
    ) on conflict do nothing;

    --- 2. temp-table resolve direct access by member edge (initial trivial access levels)
    -- resolve_direct_accesslevel_in_can_access_cache_table(userid, get_via_url_mode);
    -- these 3 update queries overwrite each other, so the order matters
    if get_via_url_mode then
        update can_access_cache
            set can_access = 'readwrite'
            from node
            where set_by_membership = false and node.id = can_access_cache.id and accesslevel = 'readwrite' and can_access <> 'readwrite';
    end if;

    -- write restrictions from node into can_access_cache
    update can_access_cache
        set can_access = 'restricted'
        from node
        where set_by_membership = false and node.id = can_access_cache.id and accesslevel = 'restricted' and can_access <> 'restricted';

    -- write accesslevel from member edge into can_access_cache
    update can_access_cache
        set can_access = (member.data->>'level')::accesslevel_tmp,
        set_by_membership = true
        from member
        where set_by_membership = false and member.source_nodeid = can_access_cache.id
            AND member.target_userid = userid;

    -- raise warning 'after member %', (select array_agg(to_json(can_access_cache)) from can_access_cache);
    -- raise warning 'after member FOUND %', FOUND;



    --- 3. propagate access down iteratively until no more updates are possible
    -- raise warning 'before propagation %: % unknown', nodeid, (select count(*) from can_access_cache where can_access = 'unknown');
    loop
        -- call propagate_permissions_in_can_access_cache_table(userid, can_access_cache);

        update can_access_cache
            set can_access = 'readwrite'
            FROM accessedge
            JOIN can_access_cache as parent_cache ON parent_cache.id = source_nodeid AND parent_cache.can_access = 'readwrite'
            JOIN node on node.id = target_nodeid -- AND node.accesslevel <> 'restricted'
            WHERE
                can_access_cache.can_access = 'unknown' and  -- only overwrite unknown rows
                (
                node.accesslevel is null or
                node.accesslevel = 'readwrite' or -- allow inheritance for all not-restricted nodes
                EXISTS
                    (SELECT *
                     FROM member
                     WHERE member.source_nodeid = accessedge.target_nodeid
                         AND member.target_userid = userid
                         AND member.data->>'level' = 'readwrite' )
                );

        -- if FOUND = true then
            -- raise warning 'updated %: % unknown', nodeid, (select count(*) from can_access_cache where can_access = 'unknown');
        -- end if;
        exit when FOUND = false;
    end loop;


    -- raise warning 'after propagate FOUND %', FOUND;
    -- raise warning 'after propagate %', (select array_agg(to_json(can_access_cache)) from can_access_cache);
    -- raise warning '';

    --- 4. return nodes ids from table which have access
    return exists(select from can_access_cache where can_access_cache.id = nodeid and can_access_cache.can_access = 'readwrite');
end;
$$ language plpgsql strict;



create function resolve_direct_accesslevel_in_can_access_cache_table(userid uuid, get_via_url_mode boolean) returns void as $$
begin
    if get_via_url_mode then
        update can_access_cache
            set can_access = 'readwrite'
            from node
            where node.id = can_access_cache.id and accesslevel = 'readwrite';
    end if;

    -- write restrictions from node into can_access_cache
    update can_access_cache
        set can_access = 'restricted'
        from node
        where node.id = can_access_cache.id and accesslevel = 'restricted';

    -- write accesslevel from member edge into can_access_cache
    update can_access_cache
        set can_access = (member.data->>'level')::accesslevel_tmp
        from member
        where member.source_nodeid = can_access_cache.id
            AND member.target_userid = userid;
end;
$$ language plpgsql strict;



create function propagate_permissions_in_can_access_cache_table(userid uuid) returns boolean as $$
-- declare
--     bla json[];
begin
-- select array_agg(to_json(can_access_cache)) into bla from can_access_cache;
-- raise warning '%', bla;
update can_access_cache
    set can_access = 'readwrite'
    FROM accessedge
    JOIN can_access_cache as parent_cache ON parent_cache.id = source_nodeid AND parent_cache.can_access = 'readwrite'
    JOIN node on node.id = target_nodeid -- AND node.accesslevel <> 'restricted'
    WHERE
        can_access_cache.can_access = 'unknown' and  -- only overwrite unknown rows
        (
        node.accesslevel is null or
        node.accesslevel = 'readwrite' -- allow inheritance for all not-restricted nodes
        or
        EXISTS
            (SELECT *
             FROM member
             WHERE member.source_nodeid = accessedge.target_nodeid
                 AND member.target_userid = userid
                 AND member.data->>'level' = 'readwrite' )
        );

return FOUND;
end;
$$ language plpgsql strict;



create function can_access_node_expecting_cache_table(userid uuid, nodeid uuid, get_via_url_mode boolean default false) returns boolean as $$
begin
    IF NOT EXISTS (select 1 from node where id = nodeid) then return true; end if; -- everybody has full access to non-existant nodes
    return can_access_node_recursive(userid, nodeid, get_via_url_mode);
end;
$$ language plpgsql strict;

create procedure create_access_cache_table() as $$
    create temporary table can_access_cache (id uuid NOT NULL, can_access accesslevel_tmp NOT NULL, set_by_membership boolean NOT NULL) on commit drop;
    create unique index on can_access_cache (id);
    create index on can_access_cache (can_access);
    create index on can_access_cache (set_by_membership);
$$ language sql;

create function can_access_node_providing_cache_table(userid uuid, nodeid uuid, get_via_url_mode boolean default false) returns boolean as $$
declare
    result boolean;
begin
    call create_access_cache_table();

    result := can_access_node_expecting_cache_table(userid, nodeid, get_via_url_mode);

    drop table can_access_cache;
    -- raise warning 'can_access_node: dropped table';

    return result;
end;
$$ language plpgsql strict;

create function can_access_node(userid uuid, nodeid uuid) returns boolean as $$
    select can_access_node_providing_cache_table(userid, nodeid, false);
$$ language sql strict;

create function can_access_node_via_url(userid uuid, nodeid uuid) returns boolean as $$
    select can_access_node_providing_cache_table(userid, nodeid, true);
$$ language sql strict;

-- returns nodeids which the user does not have permission for
create function inaccessible_nodes(userid uuid, nodeids uuid[]) returns setof uuid[] as $$
begin
    call create_access_cache_table();

    return query select COALESCE(array_agg(id), array[]::uuid[]) from (select unnest(nodeids) id) ids where not can_access_node_expecting_cache_table(userid, id);

    drop table can_access_cache;
    -- raise warning 'inaccessible_nodes: dropped table';
    return;
end
$$ language plpgsql strict;

-- IMPLEMENTATIONS
CREATE FUNCTION array_intersect(anyarray, anyarray)
  RETURNS anyarray
  language sql immutable
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

-- create function traverse_step_with_permission(id uuid) returns setof uuid as $$
--     select contentedge.target_nodeid
--     FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
--         and can_access_node_expecting_cache(userid, edge.contentedge.target_nodeid)
--     union
--     select useredge.target_userid
--     FROM content INNER JOIN useredge ON useredge.source_nodeid = content.id
--         and can_access_node_expecting_cache(userid, useredge.source_nodeid)
-- $$ language sql stable strict;



-- create function down_traversal_in_can_access_cache_table() returns void as $$
-- -- TODO: optimization only traverse resolved nodes down
-- INSERT INTO can_access_cache
--     SELECT target_nodeid, 'unknown'
--     FROM contentedge
--     JOIN can_access_cache ON can_access_cache.id = source_nodeid
--         AND can_access = 'readwrite'

-- ON conflict (id) DO NOTHING
-- $$ language sql strict;






-- create function resolve_direct_node_permissions_in_can_access_cache_table() returns void as $$
-- $$ language sql strict;







-- create function can_access_node(nodes uuid[], userid uuid) returns setof uuid $$
    --- 1. get all transitive_parents of nodes and write them into temp table
    --- 2. temp-table resolve direct access by member edge (initial trivial access levels)
    --- 3. propagate access down iteratively
    --- 4. return nodes ids from table which have access
-- $$


--create function graph_traversed_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
--declare 
--    updated int;
--begin
--    -- 1. content = page_parents + deep children (over content edges)
--    -- 2. direct parents of content
--    -- 3. transitive_parents of page_parents
--    -- EVERYTHING FILTERED BY ACCESS

--    ----------------------------------




    -- call create_access_cache_table();

--    -- TODO: get all transitive parents of page_parents and check for direct access (not inherited access) by readwrite-member edges or node.accesslevel = 'readwrite'.
--    -- put the accessible nodeids into can_access_cache table with level 'readwrite'

--    execute
--    insert into temporary
--    (
--        with recursive
--        transitive_parents(id) AS (
--            select id from node where id = any(page_parents)
--            union
--            select contentedge.source_nodeid
--                FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id

--        )
--        select id, 'unknown' from transitive_parents
--    );

--    execute resolve_direct_accesslevel_in_can_access_cache_table(userid);

--    loop
--        execute down_traversal_in_can_access_cache_table();
--        updated := execute propagate_permissions_in_can_access_cache_table();
--        exit when updated = 0
--    end loop

--    -- TODO get all direct parents of all nodes (and check permissions)

--    return query
--    select id from can_access_cache where level = 'readwrite';

--    drop table can_access_cache;
--    return;
--end
--$$ language plpgsql strict;



create function graph_traversed_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
declare 
    accessible_page_parents uuid[];
begin
    accessible_page_parents := (select array_agg(id) from node where id = any(page_parents) and can_access_node_expecting_cache_table(userid, id));

    return query
    with recursive
        content(id) AS (
            select id from node where id = any(accessible_page_parents) -- strangely this is faster than `select unnest(starts)`
            union -- discards duplicates, therefore handles cycles and diamond cases
            select contentedge.target_nodeid
                FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
                    and can_access_node_expecting_cache_table(userid, contentedge.target_nodeid)
        ),
        transitive_parents(id) AS (
            select id from node where id = any(accessible_page_parents)
            union
            select contentedge.source_nodeid
                FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
                    and can_access_node_expecting_cache_table(userid, contentedge.source_nodeid)
        )

        -- all transitive children
        select * from content
        union
        -- direct parents of content, useful to know tags of content nodes
        select edge.sourceid from content INNER JOIN edge ON edge.targetid = content.id and can_access_node_expecting_cache_table(userid, edge.sourceid)
        union
        -- transitive parents describe the path/breadcrumbs to the page
        select * FROM transitive_parents;
end
$$ language plpgsql strict;


-- induced_subgraph assumes that access is already checked
create function induced_subgraph(nodeids uuid[])
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], targetids uuid[], edgeData text[])
as $$
    select
    node.id, node.data, node.role, node.accesslevel, node.views, -- all node columns
    array_remove(array_agg(edge.targetid), NULL), array_remove(array_agg(edge.data::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from node
    left outer join edge on edge.sourceid = node.id and edge.targetid = ANY(nodeids) -- outer join, because we want to keep the nodes which have no outgoing edges
    where node.id = ANY(nodeids)
    group by (node.id, node.data, node.role, node.accesslevel) -- needed for multiple outgoing edges
$$ language sql strict;

create function user_quickaccess_nodes(userid uuid) returns setof uuid as $$
    with recursive channels(id) as (
        -- all pinned channels of the user
        select source_nodeid from pinned where pinned.target_userid = userid and can_access_node_expecting_cache_table(userid, pinned.source_nodeid)
        union
        -- all invitations of the user
        select source_nodeid from invite where invite.target_userid = userid and can_access_node_expecting_cache_table(userid, invite.source_nodeid)
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select child.source_parentid FROM channels INNER JOIN child ON child.target_childid = channels.id and can_access_node_expecting_cache_table(userid, child.source_parentid)
    )
    select * from channels;
$$ language sql strict;

create function readable_graph_page_nodes_with_channels(parents uuid[], userid uuid)
returns setof uuid
as $$
    select cs.nodeid from (select user_quickaccess_nodes(userid) as nodeid) as cs
    union
    select * from graph_traversed_page_nodes(parents, userid) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
$$ language sql strict;


-- create function readable_graph_page_nodes_with_channels_with_orphans(parents uuid[], children uuid[], userid uuid)
-- returns table(nodeid uuid)
-- as $$
--     select cs.nodeid from (select user_quickaccess_nodes(userid) as nodeid) as cs
--     union
--     select unnest(graph_traversed_page_nodes(parents, children, userid)) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
--     union
--     select id from node where id not in (select sourceid from edge where data->>'type' = 'Child') and can_access_node_expecting_cache_table(userid, id)
-- $$ language sql;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], targetids uuid[], edgeData text[])
as $$
begin
call create_access_cache_table();

return query select * from induced_subgraph(
    array(
        with nodes as (select readable_graph_page_nodes_with_channels(parents, userid) as nodeid)
        select nodes.nodeid from nodes
        union
        select useredge.target_userid from nodes inner join useredge on useredge.source_nodeid = nodes.nodeid
        union
        select userid
    )
);

drop table can_access_cache;
-- raise warning 'graph_page: dropped table';
return;
end
$$ language plpgsql strict;


-- page(parents, children) -> graph as adjacency list
-- create function graph_page_with_orphans(parents uuid[], children uuid[], userid uuid)
-- returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
-- as $$
-- select induced_subgraph(
--     array(
--         with
--             nodes as (select readable_graph_page_nodes_with_channels_with_orphans(parents, children, userid) as nodeid),
--             users as (select edge.sourceid as userid from edge, nodes where edge.targetid = nodes.nodeid and (edge.data->>'type' = 'Author' or edge.data->>'type' = 'Member')) -- all available authors and members for all nodes
--         select nodeid from nodes
--         union
--         select userid from users
--         union
--         select userid
--     )
-- );
-- $$ language sql;









-- this works on nodes, not only users. maybe restrict?
CREATE FUNCTION mergeFirstUserIntoSecond(oldUser uuid, keepUser uuid) RETURNS VOID AS $$
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
    record record;
 BEGIN

 FOR table_record IN tables LOOP
    -- insert record with updated target columns into the target table
    execute format('insert into %I (select ( json_populate_record(%I, json_build_object(''%I'', ''%s'')) ).* from %I where %I = ''%s'') on conflict do nothing', table_record.table, table_record.table, table_record.col, keepUser, table_record.table, table_record.col, oldUser);

    -- deleted the newly inserted records
    execute format('delete from %I where %I = ''%s''', table_record.table, table_record.col, oldUser);
 END LOOP;

 delete from usernode where id = oldUser;

 END;
$$ LANGUAGE plpgsql strict;






CREATE or replace FUNCTION now_utc() RETURNS TIMESTAMP AS $$
    select NOW() AT TIME ZONE 'utc';
$$ language sql stable;








create function notified_users_at_deepest_node(startids uuid[], now timestamp default now_utc())
    returns table (
        userid uuid             -- user id who will be notified
        , initial_nodes uuid[]  -- nodes the user will be notified about
        , subscribed_node uuid  -- node at which the user set to be notified (e.g. channel)
    ) as $$
with recursive notified_users(
    initial_node        -- nodes the user may be notified about
    , userid            -- user id who will be notified
    , subscribed_node   -- node at which the user set to be notified (e.g. channel)
    , allowed_members   -- all users / members with access to the initial node and hence to be checked against
    , inspected_node    -- current node that was traversed
    , visited           -- all nodes that have been visited (prevent cycles)
    , depth
) as (
    select
        node.id as initial_node
        , (case                                                                                 -- Assumptions: 1.) No memberships that add restrictions
            when node.accesslevel = 'restricted'                                                --\
                and notify.target_userid = any(                                                 ---\
                    select target_userid from member where member.data->>'level'='readwrite'    ----\ Node is restricted, but user has readwrite membership
                        and member.source_nodeid = node.id                                      ----/ => User is allowed to acces the node
                )                                                                               ---/
                then notify.target_userid                                                       --/
            when node.accesslevel = 'restricted'                                                --\ Node ist restricted and user does not have a membership
                then null                                                                       --/ => User has no access to the node
            else notify.target_userid                                                      --> Node is public
        end) as userid
        , notify.source_nodeid as subscribed_node
        , (case    when node.accesslevel = 'restricted'                 --\
                    then array(                                         ---\
                        select member.target_userid from member         ----\  Add memberships only when the initial node is restricted
                            where member.data->>'level'='readwrite'     ----/
                                and member.source_nodeid = node.id      ---/
                        )                                               --/
                else null::uuid[]                                       --> No restrictions => No membeships needed
        end) as allowed_members
        , node.id as inspected_node
        , array[node.id] as visited
        , 0 as depth
        from node
        left outer join notify on notify.source_nodeid = node.id
        where node.id = any(startids)

    union

    select
        notified_users.initial_node as initial_node     -- initial_nodes are propageted in each step
        , (case   when node.accesslevel = 'restricted'              --\
                    and notify.target_userid = any(                 ---\
                        select target_userid from member            ----\  There is a notifyedge on a restricted node and user has readwrite membership
                            where member.data->>'level'='readwrite' -----> => User is allowed to acces the node
                                and member.source_nodeid = node.id  ----/
                    )                                               ---/
                then notify.target_userid                           --/
                when node.accesslevel = 'restricted'                --\ Restricted node and no access
                then null                                           --/ => User has no access to the node
                else notify.target_userid                           --> No restrictions => No membeships needed
        end) as userid
        , notify.source_nodeid as subscribed_node
        , (case    when node.accesslevel = 'restricted'
						and notified_users.allowed_members is null
                    then array(
						select target_userid from member
							where member.data->>'level'='readwrite'
							and member.source_nodeid = node.id
					)
                	when node.accesslevel = 'restricted'
                    	then array(
							select unnest(notified_users.allowed_members)
								intersect
							(select target_userid from member
									where member.data->>'level'='readwrite'
									and member.source_nodeid = node.id
							)
						)
                else notified_users.allowed_members
        end) as allowed_members
        , node.id as inspected_node
        , array_append(notified_users.visited, child.source_parentid) as visited
        , (notified_users.depth + 1) as depth
    from notified_users
    inner join child on child.target_childid = notified_users.inspected_node
        and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
        and not child.source_parentid = any(visited)
    inner join node on node.id = child.source_parentid
    left outer join notify on notify.source_nodeid = node.id

) select
    notification_result.userid
    , array_agg(notification_result.initial_node)
    , notification_result.subscribed_node
	from notified_users notification_result
    left outer join notified_users notification_filter on notification_result.userid = notification_filter.userid and notification_result.initial_node = notification_filter.initial_node and (notification_result.depth > notification_filter.depth)
    where notification_filter.userid is null and notification_result.userid is not null
        and case  when notification_result.allowed_members is null
                    then true
                else notification_result.userid = any(notification_result.allowed_members)
        end
    group by notification_result.userid, notification_result.subscribed_node;
$$ language sql stable;


create function notified_users_by_nodeid(startids uuid[])
    returns table (
         userid uuid, notifiedNodes uuid[], subscribedNodeId uuid, subscribedNodeContent text
    ) as $$
    select notifications.userid, notifications.initial_nodes as notifiedNodes, notifications.subscribed_node as subscribedNodeId, node.data->>'content' as subscribedNodeContent
        from notified_users_at_deepest_node(startids) as notifications
        inner join node on node.id = notifications.subscribed_node
$$ language sql stable;

-- rollback;
