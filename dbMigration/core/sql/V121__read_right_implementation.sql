alter table node_can_access_mat
    ADD COLUMN accesslevel accesslevel NOT NULL DEFAULT 'readwrite';

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
 drop trigger node_update_trigger on node;
 drop function node_update;
 drop trigger edge_insert_trigger on edge;
 drop function edge_insert;
 drop trigger edge_update_trigger on edge;
 drop function edge_update;
 drop trigger edge_delete_trigger on edge;
 drop function edge_delete;
 drop function node_can_access_users_multiple;
 drop function node_can_access_users;
 drop function node_can_access;
 drop function ensure_recursive_node_can_access;
 drop function node_can_access_deep_children;

drop function graph_page;
drop function graph_traversed_page_nodes;
drop function user_bookmarks;
drop function mergeFirstUserIntoSecond;
drop function inaccessible_nodes;
drop function notified_users_by_nodeid;
drop function notified_users_at_deepest_node;
drop function now_utc;
drop function millis_to_timestamp;

drop aggregate array_merge_agg(anyarray);
drop function array_intersect;
drop function array_merge;

----------------------------------------------------------
-- NODE CAN ACCESSS

-- trigger for node update
create function node_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF ((new.accesslevel is null and old.accesslevel is not null) or (new.accesslevel is not null and old.accesslevel is null) or new.accesslevel <> old.accesslevel) THEN
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.id));
    end if;
    return new;
  end;
$$;
create trigger node_update_trigger before update on node for each row execute procedure node_update();

-- trigger edge insert
create function edge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.data->>'type' = 'Child' or new.data->>'type' = 'LabeledProperty') THEN
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
    end IF;
    return new;
  end;
$$;
create trigger edge_insert_trigger before insert on edge for each row execute procedure edge_insert();

-- trigger edge update
create function edge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (new.sourceid <> old.sourceid or new.targetid <> old.targetid or new.data->>'type' <> old.data->>'type') THEN
        IF (new.data->>'type' = 'Child' or new.data->>'type' = 'LabeledProperty') THEN
            delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
        ELSIF(new.data->>'type' = 'Member') THEN
            --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
            delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
        end IF;
    end IF;
    return new;
  end;
$$;
create trigger edge_update_trigger before insert on edge for each row execute procedure edge_update();

-- trigger for edge delete
create function edge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    IF (old.data->>'type' = 'Child' or old.data->>'type' = 'LabeledProperty') THEN
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    ELSIF(old.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.sourceid));
    end IF;
    return old;
  end;
$$;
create trigger edge_delete_trigger before delete on edge for each row execute procedure edge_delete();

-- deep access children of node
create function node_can_access_deep_children(node_id uuid) returns table(id uuid) as $$
    with recursive
        content(id) AS (
            select id from node where id = node_id
            union -- discards duplicates, therefore handles cycles and diamond cases
            -- TODO: can_access_deep_children must only be invalidated when they inherit members
            select accessedge.target_nodeid FROM content INNER JOIN accessedge ON accessedge.source_nodeid = content.id
        )
        -- all transitive children
        select id from content;
$$ language sql stable;

-- recursively check whether a node is accessible. will use cache if valid and otherwise with side-effect of filling the cache.
-- returns uncacheable node_ids - nodes that we cannot cache yet because of incomplete knowledge (cycles due with this visited array).
create function ensure_recursive_node_can_access(node_id uuid, visited uuid[]) returns uuid[] as $$
declare
    uncachable_node_ids uuid[] default array[]::uuid[];
    is_complete boolean;
begin
    IF ( node_id = any(visited) ) THEN return array[node_id]; end if; -- prevent inheritance cycles

    IF ( not exists(select 1 from node_can_access_mat where node_can_access_mat.nodeid = node_id and node_can_access_mat.complete) ) THEN

        -- if node access level is inherited or public, check above, else just this level
        IF (exists(select 1 from node where id = node_id and (accesslevel IS NULL or accesslevel = 'read' or accesslevel = 'readwrite'))) THEN -- null means inherit for the node, readwrite/public or read/public inherits as well

            -- recursively inherit permissions from parents. run all ensure_recursive_node_can_access
            -- intersect the uncachable_node_ids with the visited array. We can start caching as soon as there are no uncachable_node_ids from the visited array.
            uncachable_node_ids := (select array(
                select unnest(ensure_recursive_node_can_access(accessedge.source_nodeid, visited || node_id)) from accessedge where accessedge.target_nodeid = node_id and accessedge.source_nodeid <> node_id
                intersect
                select unnest(visited)
            ));

            -- if there are not uncachable_node_ids, we can create complete records that can be used without needing calculation.
            -- if there are uncachable_node_idsa, then we cannot say whether the result is complete or missing some users. therefore we create an incomplete record.
            -- incomplete records can be used in aggregation in our recursion, they are not wrong, but might be missing users.
            is_complete := cardinality(uncachable_node_ids) = 0;
            insert into node_can_access_mat (
                select node_id as nodeid, node_can_access_mat.userid as userid, is_complete as complete, node_can_access_mat.accesslevel as accesslevel
                from accessedge
                inner join node_can_access_mat
                on node_can_access_mat.nodeid = accessedge.source_nodeid
                where accessedge.target_nodeid = node_id and accessedge.source_nodeid <> node_id
                union
                select node_id as nodeid, member.target_userid as userid, is_complete as complete, (member.data->>'level')::accesslevel as accesslevel
                from member
                where member.source_nodeid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = is_complete;
        ELSE
            insert into node_can_access_mat (
                select node_id as nodeid, member.target_userid as userid, true as complete, (member.data->>'level')::accesslevel as accesslevel
                from member
                where member.source_nodeid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = true;
        END IF;
    end if;

    return uncachable_node_ids;
end;
$$ language plpgsql strict;



create function node_can_access_users(node_id uuid, min_level accesslevel) returns table(userid uuid) as $$
begin
    perform ensure_recursive_node_can_access(node_id, array[]::uuid[]);

    return query
        select node_can_access_mat.userid
        from node_can_access_mat where node_can_access_mat.nodeid = node_id and node_can_access_mat.accesslevel >= min_level;
end;
$$ language plpgsql strict;

create function node_can_access_users_multiple(node_ids uuid[], min_level accesslevel) returns table(nodeid uuid, userid uuid) as $$
begin
    perform ensure_recursive_node_can_access(ids.id, array[]::uuid[]) from (select unnest(node_ids) id) ids;

    return query
        select node_can_access_mat.nodeid, node_can_access_mat.userid
        from node_can_access_mat where node_can_access_mat.nodeid = any(node_ids) and node_can_access_mat.accesslevel >= min_level;
end;
$$ language plpgsql strict;

create function node_can_access(node_id uuid, user_id uuid, min_level accesslevel) returns boolean as $$
declare
    cached_access boolean;
begin
    cached_access := (
        select bool_or(node_can_access_mat.userid = user_id)
        from node_can_access_mat
        where node_can_access_mat.nodeid = node_id and node_can_access_mat.complete and node_can_access_mat.accesslevel >= min_level
    );
    if (cached_access is not null) then return cached_access; end if;

    return not exists (
        select 1 from node
        where id = node_id
    ) or exists(
        select 1 from node_can_access_users(node_id, min_level) as node_access where node_access.userid = user_id
    );
end;
$$ language plpgsql strict;


create function inaccessible_nodes(node_ids uuid[], user_id uuid, min_level accesslevel) returns setof uuid as $$
    select ids.id from (select unnest(node_ids) id) ids where not node_can_access(ids.id, user_id, min_level);
$$ language sql strict;

--------------------------------------------------------------------------------------------------------
-- UTILITIES

CREATE FUNCTION now_utc() RETURNS TIMESTAMP AS $$
    select NOW() AT TIME ZONE 'utc';
$$ language sql stable;

create function millis_to_timestamp(millis anyelement) returns timestamp with time zone as $$
    select to_timestamp(millis::bigint / 1000)
$$ language sql immutable;

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

-----------------------------------------------------------------------------------------------------
--GRAPH PAGE

create function graph_traversed_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
    with recursive content(id) AS (
        select id from node where id = any(page_parents) and node_can_access(id, userid, 'read'::accesslevel) -- strangely this is faster than `select unnest(starts)`
        union -- discards duplicates, therefore handles cycles and diamond cases
        select contentedge.target_nodeid
            FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
            where node_can_access(contentedge.target_nodeid, userid, 'read'::accesslevel)
    ),
    transitive_parents(id) AS (
        select id from node where id = any(page_parents) and node_can_access(id, userid, 'read'::accesslevel)
        union
        select contentedge.source_nodeid
            FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
            where node_can_access(contentedge.source_nodeid, userid, 'read'::accesslevel)
    )

    -- all transitive children
    select * from content
    union
    -- direct parents of content, useful to know tags of content nodes
    select contentedge.source_nodeid from content INNER JOIN contentedge ON contentedge.target_nodeid = content.id where node_can_access(contentedge.source_nodeid, userid, 'read'::accesslevel)
    union
    -- transitive parents describe the path/breadcrumbs to the page
    select * FROM transitive_parents;
$$ language sql strict;


create function user_bookmarks(userid uuid, now timestamp default now_utc()) returns setof uuid as $$
    with recursive bookmarks(id) as (
        -- all pinned/invited channels of the user
        select sourceid as id from edge where edge.targetid = userid and data->>'type' = any(array['Invite', 'Pinned']) and node_can_access(sourceid, userid, 'read'::accesslevel)
        union
        -- all transitive project children for sidebar
        select child.target_childid as id FROM bookmarks
        INNER JOIN child ON child.source_parentid = bookmarks.id
            and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
        INNER JOIN node on node.id = child.target_childid
        where node.role->>'type' = 'Project' and node_can_access(child.target_childid, userid, 'read'::accesslevel)
    ),
    bookmarks_and_parents(id) as (
        select id from bookmarks
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select child.source_parentid as id FROM bookmarks_and_parents
        INNER JOIN child ON child.target_childid = bookmarks_and_parents.id
            and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
        where node_can_access(child.source_parentid, userid, 'read'::accesslevel)
    )
    select * from bookmarks_and_parents;
$$ language sql strict;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], settings jsonb, sourceid uuid, targetid uuid, edgeData jsonb)
as $$
    -- accessible nodes from page
    with content_node_ids as (
        select * from user_bookmarks(userid) as id -- all channels of user, inlining is slower
        union
        select * from graph_traversed_page_nodes(parents, userid) as id -- all nodes, specified by page (transitive children + transitive parents), inlining is slower
    ),
    -- content node ids and users joined with node
    all_node_ids as (
        select id from content_node_ids
        union
        select useredge.target_userid as id from content_node_ids inner join useredge on useredge.source_nodeid = content_node_ids.id
        union
        select userid as id
    )

    ---- induced subgraph of all nodes without edges - what kind of node has no edges?
    --select node.id, node.data, node.role, node.accesslevel, node.views, array[]::uuid[], array[]::text[]
    --from node
    --inner join all_node_ids on all_node_ids.id = node.id
    --where not exists(select 1 from edge where node.id = edge.sourceid)

    --union all

    -- all nodes
    select node.id, node.data, node.role, node.accesslevel, node.views, node.settings, null::uuid, null::uuid, null::jsonb -- all node columns
    from all_node_ids
    inner join node on node.id = all_node_ids.id

    union all

    -- induced edges
    select null, null, null, null, null, null, edge.sourceid, edge.targetid, edge.data -- all edge columns
    from all_node_ids
    inner join edge on edge.sourceid = all_node_ids.id
    and exists (select 1 from all_node_ids where all_node_ids.id = edge.targetid)
$$ language sql strict;

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

------------------------------------------------------------------------------------------------------------------------
-- NOTIFICATIONS

create function notified_users_at_deepest_node(startids uuid[], now timestamp default now_utc())
    returns table (
        userid uuid,             -- user id who will be notified
        initial_nodes uuid[],  -- nodes the user will be notified about
        subscribed_node uuid  -- node at which the user set to be notified (e.g. channel)
    ) as $$
with recursive notified_users(
    initial_node,        -- nodes the user may be notified about
    userid,            -- user id who will be notified
    subscribed_node,   -- node at which the user set to be notified (e.g. channel)
    inspected_node    -- current node that was traversed
) as (
    select
        node.id as initial_node,
        node_access.userid as userid,
        notify.source_nodeid as subscribed_node,
        node.id as inspected_node
    from node_can_access_users_multiple(startids , 'read') as node_access
    inner join node on node.id = node_access.nodeid
    left outer join notify on notify.source_nodeid = node.id and notify.target_userid = node_access.userid

    union

    select
        notified_users.initial_node as initial_node,     -- initial_nodes are propageted in each step
        notified_users.userid as userid,
        notify.source_nodeid as subscribed_node,
        node.id as inspected_node
    from notified_users
    inner join child on child.target_childid = notified_users.inspected_node
        and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
    inner join node on node.id = child.source_parentid
    left outer join notify on notify.source_nodeid = node.id and notify.target_userid = notified_users.userid
    where notified_users.subscribed_node is null and node_can_access(node.id, notified_users.userid, 'read'::accesslevel)

)
    select
        notification_result.userid,
        array_agg(notification_result.initial_node),
        notification_result.subscribed_node
    from notified_users notification_result
    where notification_result.subscribed_node is not null
    group by notification_result.userid, notification_result.subscribed_node;
$$ language sql strict;


create function notified_users_by_nodeid(startids uuid[])
    returns table (
         userid uuid, notifiedNodes uuid[], subscribedNodeId uuid, subscribedNodeContent text
    ) as $$
    select notifications.userid, notifications.initial_nodes as notifiedNodes, notifications.subscribed_node as subscribedNodeId, node.data->>'content' as subscribedNodeContent
        from notified_users_at_deepest_node(startids) as notifications
        inner join node on node.id = notifications.subscribed_node
$$ language sql strict;

