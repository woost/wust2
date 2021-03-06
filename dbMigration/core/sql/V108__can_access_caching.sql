CREATE TYPE can_access_result AS ENUM ('unknown', 'forbidden', 'allowed');

create function can_access_agg_fun(accum can_access_result, curr can_access_result) returns can_access_result AS $$
    select (
        CASE
        WHEN accum = 'allowed' THEN 'allowed'
        ELSE curr
        END
    );
$$ LANGUAGE sql strict;
create aggregate can_access_agg(can_access_result)
(
    INITCOND = 'forbidden',
    STYPE = can_access_result,
    SFUNC = can_access_agg_fun
);

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

drop aggregate can_access_agg(can_access_result);
drop function can_access_agg_fun;

drop aggregate array_merge_agg(anyarray);
drop function array_intersect;
drop function array_merge;

create function millis_to_timestamp(millis anyelement) returns timestamp with time zone as $$
    select to_timestamp(millis::bigint / 1000)
$$ language sql stable;

create function can_access_agg_fun(accum can_access_result, curr can_access_result) returns can_access_result AS $$
    select (
        CASE
        WHEN accum = 'allowed' THEN 'allowed'
        ELSE curr
        END
    );
$$ LANGUAGE sql strict;
create aggregate can_access_agg(can_access_result)
(
    INITCOND = 'forbidden',
    STYPE = can_access_result,
    SFUNC = can_access_agg_fun
);

-- recursively check whether a node is accessible.
-- non-existing user: we assume that a user exist in any cases and therefore we do not handle this explicitly
create function can_access_node_recursive(userid uuid, nodeid uuid, visited uuid[], get_via_url_mode boolean) returns can_access_result as $$
declare
    member_access_level accesslevel;
    node_access_level accesslevel;
    result boolean;
    access_result can_access_result;
begin
    select can_access into result from can_access_cache where id = nodeid limit 1;
    IF ( result = true ) THEN return 'allowed'; end if;
    IF ( result = false ) THEN return 'forbidden'; end if;
    IF ( nodeid = any(visited) ) THEN return 'unknown'; end if; -- prevent inheritance cycles

    -- is there a membership?
    select data->>'level' into member_access_level from member where member.target_userid = userid and member.source_nodeid = nodeid limit 1;
    IF (member_access_level IS NOT NULL) THEN -- if member edge exists
        -- either the use is granted readwrite, then he can access. otherwise not.
        result := member_access_level = 'readwrite';
        insert into can_access_cache VALUES (nodeid, result);
        IF ( result = true ) THEN return 'allowed'; end if;
        return 'forbidden';
    END IF;

    -- if no member edge exists, read access level directly from node
    select accessLevel into node_access_level from node where id = nodeid limit 1;

    -- get_via_url_mode allows access to public nodes, you can become a member of these nodes.
    IF (get_via_url_mode and node_access_level = 'readwrite') THEN return 'allowed'; END IF;

    -- if node access level is inherited or public, check above, else not grant access.
    IF (node_access_level IS NULL or node_access_level = 'readwrite') THEN -- null means inherit for the node
        -- recursively inherit permissions from parents. minimum one parent needs to allowed access.
        select can_access_agg(can_access_node_recursive(userid, accessedge.source_nodeid, visited || nodeid, get_via_url_mode)) into access_result from accessedge where accessedge.target_nodeid = nodeid;
        -- return unknown if we cannot get a recursive answer, because of a cycle
        -- if this is because of visited, then we cannot know whether this node is accessible
        -- but if visited is empty, we know for sure that this node is inaccessible.
        if access_result = 'unknown' and cardinality(visited) > 0 then return 'unknown'; end if;
        result := access_result = 'allowed';
        insert into can_access_cache VALUES (nodeid, result);
        IF ( result = true ) THEN return 'allowed'; end if;
        return 'forbidden';
    END IF;

    return 'forbidden';
end;
$$ language plpgsql strict;

create or replace function can_access_node_expecting_cache_table(userid uuid, nodeid uuid, get_via_url_mode boolean default false) returns boolean as $$
begin
    IF NOT EXISTS (select 1 from node where id = nodeid) then return true; end if; -- everybody has full access to non-existant nodes
    return can_access_node_recursive(userid, nodeid, array[]::uuid[], get_via_url_mode) = 'allowed';
end;
$$ language plpgsql strict;

create function can_access_node_providing_cache_table(userid uuid, nodeid uuid, get_via_url_mode boolean default false) returns boolean as $$
declare
    result boolean;
begin
    create temporary table can_access_cache (id uuid NOT NULL, can_access boolean) on commit drop;
    create unique index on can_access_cache (id);

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
    create temporary table can_access_cache (id uuid NOT NULL, can_access boolean) on commit drop;
    create unique index on can_access_cache (id);

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
create temporary table can_access_cache (id uuid NOT NULL, can_access boolean) on commit drop;
create unique index on can_access_cache (id);

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

