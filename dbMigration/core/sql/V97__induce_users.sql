drop function graph_page;

create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], targetids uuid[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with nodes as (select readable_graph_page_nodes_with_channels(parents, userid) as nodeid)
        select nodeid from nodes
        union
        select useredge.target_userid from nodes inner join useredge on useredge.source_nodeid = nodes.nodeid
        union
        select userid
    )
);
$$ language sql stable strict;

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
drop function can_access_node_in_down_traversal;
drop function inaccessible_nodes;
drop function subscriptions_by_nodeid;
drop function notified_users_search_fast;
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
create function can_access_node_recursive(userid uuid, nodeid uuid, visited uuid[] default array[]::uuid[]) returns boolean as $$
declare
    node_access_level accesslevel;
    result boolean;
begin
    IF ( nodeid = any(visited) ) THEN return false; end if; -- prevent inheritance cycles

    -- is there a membership?
    select data->>'level' into node_access_level from member where member.target_userid = userid and member.source_nodeid = nodeid limit 1;
    IF (node_access_level IS NULL) THEN -- if no member edge exists
        -- read access level directly from node
        select accessLevel into node_access_level from node where id = nodeid limit 1;
        IF (node_access_level IS NULL) THEN -- null means inherit for the node
            -- recursively inherit permissions from parents. minimum one parent needs to allow access.
            -- select bool_or(can_access_node_recursive(userid, edge.sourceid, visited || nodeid)) into result from edge where edge.targetid = nodeid;
            select bool_or(can_access_node_recursive(userid, contentedge.source_nodeid, visited || nodeid)) into result from contentedge where contentedge.target_nodeid = nodeid;
            return COALESCE(result, false); -- if there are no parents => no permission
        END IF;
    END IF;

    return COALESCE(node_access_level = 'readwrite', false); -- if no access level was found => no permission
end;
$$ language plpgsql STABLE strict;

create function can_access_node(userid uuid, nodeid uuid) returns boolean as $$
begin
    IF NOT EXISTS (select 1 from node where id = nodeid) then return true; end if; -- everybody has full access to non-existant nodes
    return can_access_node_recursive(userid, nodeid);
end;
$$ language plpgsql STABLE strict;

-- check acessibility without recursion. can be used when check permissions in
-- a down traversal that already checks permissions.
create function can_access_node_in_down_traversal(userid uuid, nodeid uuid) returns boolean as $$
declare
    node_access_level accesslevel;
    result boolean;
begin
    -- is there a membership?
    select data->>'level' into node_access_level from member where member.target_userid = userid and member.source_nodeid = nodeid limit 1;
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
$$ language plpgsql STABLE strict;

-- returns nodeids which the user does not have permission for
create function inaccessible_nodes(userid uuid, nodeids uuid[]) returns uuid[] as $$
    select COALESCE(array_agg(id), array[]::uuid[]) from (select unnest(nodeids) id) ids where not can_access_node(userid, id);
$$ language sql stable strict;

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
--         and can_access_node_in_down_traversal(userid, edge.contentedge.target_nodeid)
--     union
--     select useredge.target_userid
--     FROM content INNER JOIN useredge ON useredge.source_nodeid = content.id
--         and can_access_node_in_down_traversal(userid, useredge.source_nodeid)
-- $$ language sql stable strict;

create function graph_traversed_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
declare 
    accessible_page_parents uuid[];
begin
    accessible_page_parents := (select array_agg(id) from node where id = any(page_parents) and can_access_node(userid, id));

    return query
    with recursive
        content(id) AS (
            select id from node where id = any(accessible_page_parents) -- strangely this is faster than `select unnest(starts)`
            union -- discards duplicates, therefore handles cycles and diamond cases
            select contentedge.target_nodeid
                FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
                    and can_access_node_in_down_traversal(userid, contentedge.target_nodeid)
        ),
        transitive_parents(id) AS (
            select id from node where id = any(accessible_page_parents)
            union
            select contentedge.source_nodeid
                FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
                    and can_access_node(userid, contentedge.source_nodeid)
        )

        -- all transitive children
        select * from content
        union
        -- direct parents of content, useful to know tags of content nodes
        select edge.sourceid from content INNER JOIN edge ON edge.targetid = content.id and can_access_node(userid, edge.sourceid)
        union
        -- transitive parents describe the path/breadcrumbs to the page
        select * FROM transitive_parents;
end
$$ language plpgsql stable strict;


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
$$ language sql stable strict;

create function user_quickaccess_nodes(userid uuid) returns setof uuid as $$
    with recursive channels(id) as (
        -- all pinned channels of the user
        select source_nodeid from pinned where pinned.target_userid = userid and can_access_node(userid, pinned.source_nodeid)
        union
        -- all invitations of the user
        select source_nodeid from invite where invite.target_userid = userid and can_access_node(userid, invite.source_nodeid)
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select child.source_parentid FROM channels INNER JOIN child ON child.target_childid = channels.id and can_access_node(userid, child.source_parentid)
    )
    select * from channels;
$$ language sql stable strict;

create function readable_graph_page_nodes_with_channels(parents uuid[], userid uuid)
returns setof uuid
as $$
    select cs.nodeid from (select user_quickaccess_nodes(userid) as nodeid) as cs
    union
    select * from graph_traversed_page_nodes(parents, userid) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
$$ language sql stable strict;


-- create function readable_graph_page_nodes_with_channels_with_orphans(parents uuid[], children uuid[], userid uuid)
-- returns table(nodeid uuid)
-- as $$
--     select cs.nodeid from (select user_quickaccess_nodes(userid) as nodeid) as cs
--     union
--     select unnest(graph_traversed_page_nodes(parents, children, userid)) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
--     union
--     select id from node where id not in (select sourceid from edge where data->>'type' = 'Child') and can_access_node(userid, id)
-- $$ language sql;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], targetids uuid[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with nodes as (select readable_graph_page_nodes_with_channels(parents, userid) as nodeid)
        select nodeid from nodes
        union
        select useredge.target_userid from nodes inner join useredge on useredge.source_nodeid = nodes.nodeid
        union
        select userid
    )
);
$$ language sql stable strict;


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






CREATE FUNCTION now_utc() RETURNS TIMESTAMP AS $$
    select NOW() AT TIME ZONE 'utc';
$$ language sql stable;








create function notified_users_search_fast(startids uuid[], now timestamp default now_utc())
    returns table (
        userid uuid, initial_nodes uuid[], subscribed_node uuid
    ) as $$
with recursive notified_users(
    initial_node, userid, subscribed_node, allowed_members, last, visited
) as (
    select
        node.id as initial_node,
        (case   when node.accesslevel = 'restricted'
                    and notify_edge.targetid = any(select targetid from edge member_edge where member_edge.data->>'type'='Member' and member_edge.data->>'level'='readwrite' and member_edge.sourceid = node.id)
                then notify_edge.targetid
                when node.accesslevel = 'restricted'
                then null
                else notify_edge.targetid
        end) as userid,
        notify_edge.sourceid as subscribed_node,
        (case    when node.accesslevel = 'restricted'
                    then array(select targetid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.sourceid = node.id)
                else null::uuid[]
        end) as allowed_members,
        node.id as last,
        array[node.id] as visited
        from node
        left outer join edge on edge.targetid = node.id
            and edge.data->>'type'='Child'
            and (edge.data->>'deletedAt' is null or millis_to_timestamp(edge.data->>'deletedAt') > now)
        left outer join edge notify_edge on notify_edge.sourceid = node.id
            and notify_edge.data->>'type'='Notify'
        where node.id = any(startids)

    union

    select
        notified_users.initial_node as initial_node,
        (case   when node.accesslevel = 'restricted'
                    and notify_edge.targetid = any(select targetid from edge member_edge where member_edge.data->>'type'='Member' and member_edge.data->>'level'='readwrite' and member_edge.sourceid = node.id)
                then notify_edge.targetid
                when node.accesslevel = 'restricted'
                then null
                else notify_edge.targetid
        end) as userid,
        notify_edge.sourceid as subscribed_node,
        (case    when node.accesslevel = 'restricted' and notified_users.allowed_members is null
                    then array(select targetid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.sourceid = node.id)
                when node.accesslevel = 'restricted'
                    then array(select unnest(notified_users.allowed_members) intersect (select targetid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.sourceid = node.id))
                else notified_users.allowed_members
        end) as allowed_members,
        -- (case   when userid is null
        --             then edge.targetid
        --         else null
        --  end) as last,
        edge.sourceid as last,
        array_append(notified_users.visited, edge.sourceid)
    from notified_users
    inner join edge on edge.targetid = notified_users.last
        and edge.data->>'type'='Child'
        and (edge.data->>'deletedAt' is null or millis_to_timestamp(edge.data->>'deletedAt') > now)
        and not edge.sourceid = any(visited)
    inner join node on node.id = edge.sourceid
    left outer join edge notify_edge on notify_edge.sourceid = edge.sourceid
        and notify_edge.data->>'type'='Notify'

) select userid, array_agg(initial_node), subscribed_node
    from notified_users
    where userid is not null
        and case  when allowed_members is null
                    then true
                else userid = any(allowed_members)
        end
    group by userid, subscribed_node;
$$ language sql stable;





create function subscriptions_by_nodeid(startids uuid[])
    returns table (
         id integer, userid uuid, endpointUrl text, p256dh text, auth text, notifiedNodes uuid[], subscribedNodeId uuid, subscribedNodeContent text
    ) as $$
    select webpushsubscription.id, webpushsubscription.userid, webpushsubscription.endpointUrl, webpushsubscription.p256dh, webpushsubscription.auth, notifications.initial_nodes as notifiedNodes, notifications.subscribed_node as subscribedNodeId, node.data->>'content' as subscribedNodeContent
        from notified_users_search_fast(startids) as notifications
        inner join webpushsubscription on webpushsubscription.userid = notifications.userid
        inner join node on node.id = notifications.subscribed_node
$$ language sql stable;

--------------------
--- end procedures
--------------------

