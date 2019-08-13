-- CAN ACCESS CACHING
-- need to react to every way the access to a node for a user could have changed.
-- then need to update node_can_access_mat and node_can_access_public_mat table.
-- How can access change:
-- 0) add/delete node/user already handled by foreign key with cascade. edges cascaed as well when deleting nodes, so changes in children/targets will be handled in edge triggers.
-- 1) update node if permission has changed, because self and deep children might have different access now. if restricted then permission are stricter now, need to invalidate all child node access and recalculate. if inherited or public permission, access could be expanded, just need to recalculate (inherited and public behave the same, only public nodes can be accessed via url).
-- 2) insert child/labelledproperty, then deep children/targets might have different access now if permission is public or inherit. no need to invalidate, can only have more access
-- 3) delete child/labelledproperty. if inherited or public permission, access could be stricter now. Need to invalidate deep children/targets and recalculate.
-- 4) insert member. need to recalute that specific user in deep children/targets.
-- 5) delete member. need to invalidate and recalute that specific user in deep children/targets.




-- TODO
-- rename can_access_node_recursive to ensure ...

drop aggregate can_access_agg(can_access_result);
drop function can_access_agg_fun;
drop function can_access_node_recursive;
drop function can_access_node;
drop function can_access_node_via_url;
drop function can_access_node_expecting_cache_table;
drop function can_access_node_providing_cache_table;
drop function induced_subgraph;
drop function readable_graph_page_nodes_with_channels;
drop type can_access_result;

-- table to store invalidated nodes

-- materialized table to cache granted access for user on node (valid = false means it needs to be recalculated)
create table node_can_access_mat(
  nodeid uuid not null references node on delete cascade,
  userid uuid not null references node on delete cascade
);
create unique index on node_can_access_mat (nodeid, userid);
create index on node_can_access_mat (userid);
-- table for storing invalidated nodes whose node_can_acecss needs to recalculated
create table node_can_access_valid(
  nodeid uuid not null references node on delete cascade
);
create unique index on node_can_access_valid (nodeid);

-- materialized table to cache public nodes including inheritance (valid = false means it needs to be recalculated)
create table node_can_access_public_mat(
  nodeid uuid not null references node on delete cascade,
  valid boolean not null
);
create unique index on node_can_access_public_mat (nodeid);
create index on node_can_access_public_mat (valid);






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
-- drop trigger node_update_trigger on node;
-- drop function node_update;
-- drop trigger edge_insert_trigger on edge;
-- drop function edge_insert;
-- drop trigger edge_update_trigger on edge;
-- drop function edge_update;
-- drop trigger edge_delete_trigger on edge;
-- drop function edge_delete;
-- drop function node_can_access_users;
-- drop function node_can_access;
-- drop function node_can_access_recursive;
-- drop function node_can_access_deep_children;

drop function graph_page;
drop function graph_traversed_page_nodes;
drop function user_quickaccess_nodes;
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
    IF (new.accesslevel <> old.accesslevel) THEN
        delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(new.id));
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
        delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    ELSIF(new.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
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
            delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(new.targetid));
        ELSIF(new.data->>'type' = 'Member') THEN
            --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
            delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
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
        delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    ELSIF(old.data->>'type' = 'Member') THEN
        --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
        delete from node_can_access_valid where nodeid = ANY(select node_can_access_deep_children(old.sourceid));
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
-- returns true if uncacheable node_ids
create function node_can_access_recursive(node_id uuid, visited uuid[]) returns uuid[] as $$
declare
    uncachable_node_ids uuid[] default array[]::uuid[];
begin
    IF ( node_id = any(visited) ) THEN return array[node_id]; end if; -- prevent inheritance cycles

    IF ( not exists(select 1 from node_can_access_valid where node_can_access_valid.nodeid = node_id) ) THEN
        -- clear current access rights
        delete from node_can_access_mat where node_can_access_mat.nodeid = node_id;

        -- if node access level is inherited or public, check above, else just this level
        IF (exists(select 1 from node where id = node_id and accesslevel IS NULL or accesslevel = 'readwrite')) THEN -- null means inherit for the node, readwrite/public inherits as well

            -- recursively inherit permissions from parents. run all node_can_access_recursive
            -- intersect the uncachable_node_ids with the visited array. We can start caching as soon as there are no uncachable_node_ids from the visited array.
            uncachable_node_ids := (select array(
                select unnest(node_can_access_recursive(accessedge.source_nodeid, visited || node_id)) from accessedge where accessedge.source_nodeid <> node_id and accessedge.target_nodeid = node_id
                intersect
                select unnest(visited)
            ));

            if (cardinality(uncachable_node_ids) = 0) then
                insert into node_can_access_valid VALUES(node_id);
                insert into node_can_access_mat (
                    select node_id as nodeid, node_can_access_mat.userid as userid
                    from accessedge
                    inner join node_can_access_mat
                    on node_can_access_mat.userid = accessedge.source_nodeid
                    where accessedge.target_nodeid = node_id and exists(select 1 from node_can_access_valid where node_can_access_valid.nodeid = node_can_access_mat.nodeid)
                    union
                    select node_id as nodeid, member.target_userid as userid
                    from member
                    where data->>'level' = 'readwrite' and member.source_nodeid = node_id
                );
            end if;
        ELSE
            insert into node_can_access_valid VALUES(node_id);
            insert into node_can_access_mat (
                select node_id as nodeid, member.target_userid as userid
                from member
                where data->>'level' = 'readwrite' and member.source_nodeid = node_id
            );
        END IF;
    end if;

    return uncachable_node_ids;
end;
$$ language plpgsql strict;



create function node_can_access_users(node_id uuid) returns table(userid uuid) as $$
begin
    perform node_can_access_recursive(node_id, array[]::uuid[]);

    return query select node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = node_id;
end;
$$ language plpgsql strict;

create function node_can_access_users_multiple(node_ids uuid[]) returns table(nodeid uuid, userid uuid) as $$
begin
    perform node_can_access_recursive(ids.id, array[]::uuid[]) from (select unnest(node_ids) id) ids;

    return query select node_can_access_mat.nodeid, node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = any(node_ids);
end;
$$ language plpgsql strict;

create function node_can_access(node_id uuid, user_id uuid) returns boolean as $$
declare
    cached_access boolean;
begin
    cached_access := (
        select exists(select 1 from node_can_access_mat where node_can_access_mat.nodeid = node_id and node_can_access_mat.userid = user_id)
        from node_can_access_valid
        where node_can_access_valid.nodeid = node_id
        limit 1
    );
    if (cached_access is not null) then return cached_access; end if;

    return not exists (
        select 1 from node
        where id = node_id
    ) or exists(
        select 1 from node_can_access_users(node_id) as node_access where node_access.userid = user_id
    );
end;
$$ language plpgsql strict;


create function inaccessible_nodes(node_ids uuid[], user_id uuid) returns setof uuid as $$
    select ids.id from (select unnest(node_ids) id) ids where not node_can_access(ids.id, user_id);
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
        select id from node where id = any(page_parents) and node_can_access(id, userid) -- strangely this is faster than `select unnest(starts)`
        union -- discards duplicates, therefore handles cycles and diamond cases
        select contentedge.target_nodeid
            FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
            where node_can_access(contentedge.target_nodeid, userid)
    ),
    transitive_parents(id) AS (
        select id from node where id = any(page_parents) and node_can_access(id, userid)
        union
        select contentedge.source_nodeid
            FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
            where node_can_access(contentedge.source_nodeid, userid)
    )

    -- all transitive children
    select * from content
    union
    -- direct parents of content, useful to know tags of content nodes
    select contentedge.source_nodeid from content INNER JOIN contentedge ON contentedge.target_nodeid = content.id where node_can_access(contentedge.source_nodeid, userid)
    union
    -- transitive parents describe the path/breadcrumbs to the page
    select * FROM transitive_parents;
$$ language sql strict;


create function user_bookmarks(userid uuid) returns setof uuid as $$
    with recursive channels(id) as (
        -- all pinned channels of the user
        select source_nodeid from pinned where pinned.target_userid = userid and node_can_access(source_nodeid, userid)
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select child.source_parentid FROM channels INNER JOIN child ON child.target_childid = channels.id where node_can_access(child.source_parentid, userid)
    )
    select * from channels;
$$ language sql strict;

-- page(parents, children) -> graph as adjacency list
create function graph_page(parents uuid[], userid uuid)
returns table(nodeid uuid, data jsonb, role jsonb, accesslevel accesslevel, views jsonb[], sourceid uuid, targetid uuid, edgeData jsonb)
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
    select node.id, node.data, node.role, node.accesslevel, node.views, null::uuid, null::uuid, null::jsonb -- all node columns
    from all_node_ids
    inner join node on node.id = all_node_ids.id

    union all

    -- induced edges
    select null, null, null, null, null, edge.sourceid, edge.targetid, edge.data -- all edge columns
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

