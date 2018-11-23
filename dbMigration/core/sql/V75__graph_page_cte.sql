create view parent as select sourceid as source_childid, targetid as target_parentid,  data from edge where data->>'type' = 'Parent';
create view member as select sourceid as source_userid,  targetid as target_nodeid,    data from edge where data->>'type' = 'Member';
create view author as select sourceid as source_userid,  targetid as target_nodeid,    data from edge where data->>'type' = 'Author';
create view notify as select sourceid as source_nodeid,  targetid as target_userid,    data from edge where data->>'type' = 'Notify';
create view pinned as select sourceid as source_userid,  targetid as target_nodeid,    data from edge where data->>'type' = 'Pinned';

create view usernode as select id, data, accesslevel, role from node where data->>'type' = 'User';

create index idx_edge_targetid on edge(targetid);
create index idx_edge_type ON edge ((data ->> 'type'));

--------------------
--- begin procedures
--------------------

-- drop
drop function create_traversal_function;
drop function graph_page;
drop function graph_page_nodes;
drop function readable_graph_page_nodes_with_channels;
drop function readable_graph_page_nodes_with_channels_with_orphans;
drop function readable_channel_nodes;
drop function graph_page_with_orphans;
drop function induced_subgraph;
drop function mergeFirstUserIntoSecond;
drop function notified_nodes_for_user;
drop function now_utc;
drop function traverse_children;
drop function traverse_parents;
drop function can_access_node_recursive;
drop function can_access_node;
drop function can_access_node_in_down_traversal;
drop function inaccessible_nodes;
drop function subscriptions_by_nodeid;
drop function notified_users_search_fast;

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
    select data->>'level' into node_access_level from member where member.source_userid = userid and member.target_nodeid = nodeid limit 1;
    IF (node_access_level IS NULL) THEN -- if no member edge exists
        -- read access level directly from node
        select accessLevel into node_access_level from node where id = nodeid limit 1;
        IF (node_access_level IS NULL) THEN -- null means inherit for the node
            -- recursively inherit permissions from parents. minimum one parent needs to allow access.
            select bool_or(can_access_node_recursive(userid, parent.target_parentid, visited || nodeid)) into result from parent where parent.source_childid = nodeid;
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


create function graph_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
declare 
    accessible_page_parents uuid[];
begin
    accessible_page_parents := (select array_agg(id) from node where id = any(page_parents) and can_access_node(userid, id));

    return query
    with recursive
        -- select traverse_children as id from traverse_children(accessible_page_parents, userid)
        content(id) AS (
            select id from node where id = any(accessible_page_parents) -- strangely this is faster than `select unnest(starts)`
            union -- discards duplicates, therefore handles cycles and diamond cases
            select parent.source_childid FROM content INNER JOIN parent ON parent.target_parentid = content.id and can_access_node_in_down_traversal(userid, parent.source_childid)
        ),
        transitive_parents(id) AS (
            select id from node where id = any(accessible_page_parents) -- strangely this is faster than `select unnest(starts)`
            union -- discards duplicates, therefore handles cycles and diamond cases
            select parent.target_parentid FROM transitive_parents INNER JOIN parent ON parent.source_childid = transitive_parents.id and can_access_node(userid, parent.target_parentid)
        )

        -- all transitive children
        select * from content
        union
        -- direct parents of content, useful to know tags of content nodes
        select parent.target_parentid from content INNER JOIN parent ON parent.source_childid = content.id and can_access_node(userid, parent.target_parentid)
        union
        -- transitive parents describe the path/breadcrumbs to the page
        select * FROM transitive_parents;
end
$$ language plpgsql stable strict;


create function induced_subgraph(nodeids uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
as $$
    select
    node.id, node.data, node.role, node.accesslevel, -- all node columns
    array_remove(array_agg(edge.targetid), NULL), array_remove(array_agg(edge.data::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from node
    left outer join edge on edge.sourceid = node.id AND edge.targetid = ANY(nodeids) -- outer join, because we want to keep the nodes which have no outgoing edges
    and (
        node.id = userid or
        (edge.data->>'type' = ANY(VALUES ('Notify'))) or -- whitelisted edge
        (node.data->>'type' = 'User' and edge.data->>'type' = ANY(VALUES ('Author'), ('Member'), ('Parent'))) or -- edge source is user, but edge is whitelisted in this direction
        (node.data->>'type' != 'User' and not exists (select id from usernode where usernode.id = edge.targetid)) -- edge not between users
    )
    where node.id = ANY(nodeids)
    group by (node.id, node.data, node.role, node.accesslevel) -- needed for multiple outgoing edges
$$ language sql stable strict;

create function readable_channel_nodes(userid uuid) returns setof uuid as $$
    with recursive channels(id) as (
        -- all pinned channels of the user
        select target_nodeid from pinned where pinned.source_userid = userid and can_access_node(userid, pinned.target_nodeid)
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select parent.target_parentid FROM channels INNER JOIN parent ON parent.source_childid = channels.id and can_access_node(userid, parent.target_parentid)
    )
    select * from channels;
$$ language sql stable strict;

create function readable_graph_page_nodes_with_channels(parents uuid[], userid uuid)
returns setof uuid
as $$
    select cs.nodeid from (select readable_channel_nodes(userid) as nodeid) as cs
    union
    select * from graph_page_nodes(parents, userid) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
$$ language sql stable strict;


-- create function readable_graph_page_nodes_with_channels_with_orphans(parents uuid[], children uuid[], userid uuid)
-- returns table(nodeid uuid)
-- as $$
--     select cs.nodeid from (select readable_channel_nodes(userid) as nodeid) as cs
--     union
--     select unnest(graph_page_nodes(parents, children, userid)) as nodeid -- all nodes, specified by page (transitive children + transitive parents)
--     union
--     select id from node where id not in (select sourceid from edge where data->>'type' = 'Parent') and can_access_node(userid, id)
-- $$ language sql;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, targetids uuid[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with
            nodes as (select readable_graph_page_nodes_with_channels(parents, userid) as nodeid)
        select nodeid from nodes
        union
        -- important: having only one query for members and authors does a seq scan over edges (don't know why). Therefore we use union.
        -- all available authors for all nodes
        select edge.sourceid as userid from nodes inner join edge on edge.targetid = nodes.nodeid where edge.data->>'type' = 'Author'
        union
        -- all available members for all nodes
        select edge.sourceid as userid from nodes inner join edge on edge.targetid = nodes.nodeid where edge.data->>'type' = 'Member'
        union
        select userid
    ),
    userid
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








create or replace function notified_users_search_fast(startids uuid[])
    returns table (
        userid uuid, initial_node uuid[]
        -- , visited uuid, allowed_members uuid[], already_determined_users uuid[], levels integer, path text[]
    ) as $$
    with recursive notified_users(
        userid, initial_node, last, visited, allowed_members, already_determined_users
        -- , level, namepath
        ) as (
    select
        array[notify_edge.targetid], node.id as initial_node, node.id, array[node.id],
        case    when node.accesslevel = 'restricted'
                    then array(select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id)
                else null::uuid[]
        end,
        array(select targetid from edge where edge.data->>'type'='Notify' and edge.sourceid = node.id and
            case    when node.accesslevel = 'restricted'
                then edge.sourceid = any(array(select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id))
                    else true
            end)
        -- , 0 , array[node.data->>'content']::text[]
    from node
    left outer join edge on edge.sourceid = node.id
        and edge.data->>'type'='Parent'
    left outer join edge notify_edge on notify_edge.sourceid = node.id
        and notify_edge.data->>'type'='Notify'
    where
        node.id = any(startids)
  union
    select
        array_append(notified_users.userid, useredge.targetid), notified_users.initial_node, edge.targetid, array_append(notified_users.visited, edge.targetid),
        -- propagation goes upwards -> so the members can only be more restricted -> intersect to filter previous restrictions
        case    when node.accesslevel = 'restricted' and notified_users.allowed_members is null
                    then array(select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id)
                when node.accesslevel = 'restricted'
                    then array(select unnest(notified_users.allowed_members) intersect (select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id))
                else notified_users.allowed_members
        end,
        notified_users.already_determined_users || array(select targetid from edge where edge.data->>'type'='Notify' and sourceid=node.id and
            -- this case could be eleminated when it is guaranteed that there are no inconsistent notify edges, e.g. on nodes without access
            case    when node.accesslevel = 'restricted' and notified_users.allowed_members is null
                        then targetid = any(array(select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id))
                    when node.accesslevel = 'restricted'
                        then targetid = any(array(select unnest(notified_users.allowed_members) intersect (select sourceid from edge where edge.data->>'type'='Member' and edge.data->>'level'='readwrite' and edge.targetid = node.id)))
                    when notified_users.allowed_members is null
                        then true
                    else targetid = any(notified_users.allowed_members)
            end
        )
        -- , notified_users.level + 1, array_append(notified_users.namepath, node.data->>'content')
    from notified_users
    inner join edge on edge.sourceid = notified_users.last
        and edge.data->>'type'='Parent'
    inner join node on node.id = edge.targetid
    left outer join edge useredge on useredge.sourceid = edge.targetid
        and useredge.data->>'type'='Notify'
    where not edge.targetid = any(visited)
  ) select userid, array_agg(distinct initial_node)
        -- , visited, allowed_members, already_determined_users, level, namepath
    from (
      select unnest(array_remove(userid, null)) as userid, initial_node, -- visited, allowed_members, already_determined_users
        case    when allowed_members is null
                    then array[userid]::uuid[]
                else allowed_members
        end as allowed_members,
        already_determined_users
        -- , level, namepath
      from notified_users
    ) as pre
    where case  when pre.allowed_members is null
                    then true
                else pre.userid = any( array(
                    select unnest(pre.allowed_members) union select unnest(pre.already_determined_users)
                ))
    end
    group by pre.userid
        -- , pre.visited, pre.allowed_members, pre.already_determined_users, pre.level, pre.namepath
    -- order by pre.userid
$$ language sql stable;





create or replace function subscriptions_by_nodeid(startids uuid[])
    returns table (
         id integer, userid uuid, endpointUrl text, p256dh text, auth text, notifiedNodes uuid[]
    ) as $$
    select webpushsubscription.id, webpushsubscription.userid, webpushsubscription.endpointUrl, webpushsubscription.p256dh, webpushsubscription.auth, notifications.initial_node as notifiedNodes
        from notified_users_search_fast(startids) as notifications
        inner join webpushsubscription on webpushsubscription.userid = notifications.userid
$$ language sql stable;
--------------------
--- end procedures
--------------------
