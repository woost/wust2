
drop view assigned;
create table assignededge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Assigned')
);
create unique index on assignededge(sourceid, targetid);
create index on assignededge(sourceid);
create index on assignededge(targetid);
insert into assignededge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Assigned');

drop view author;
create table authoredge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Author')
);
-- no unique index
create index on authoredge(sourceid);
create index on authoredge(targetid);
insert into authoredge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Author');

drop view invite;
create table inviteedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Invite')
);
create unique index on inviteedge(sourceid, targetid);
create index on inviteedge(sourceid);
create index on inviteedge(targetid);
insert into inviteedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Invite');

drop view expanded;
create table expandededge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Expanded')
);
create unique index on expandededge(sourceid, targetid);
create index on expandededge(sourceid);
create index on expandededge(targetid);
insert into expandededge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Expanded');

drop view member;
create table memberedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Member')
);
create unique index on memberedge(sourceid, targetid);
create index on memberedge(sourceid);
create index on memberedge(targetid);
insert into memberedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Member');

drop view notify;
create table notifyedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Notify')
);
create unique index on notifyedge(sourceid, targetid);
create index on notifyedge(sourceid);
create index on notifyedge(targetid);
insert into notifyedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Notify');

drop view pinned;
create table pinnededge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Pinned')
);
create unique index on pinnededge(sourceid, targetid);
create index on pinnededge(sourceid);
create index on pinnededge(targetid);
insert into pinnededge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Pinned');

--drop view read;
create table readedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Read')
);
create unique index on readedge(sourceid, targetid);
create index on readedge(sourceid);
create index on readedge(targetid);
insert into readedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Read');

drop view automated;
create table automatededge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Automated')
);
create unique index on automatededge(sourceid, targetid);
create index on automatededge(sourceid);
create index on automatededge(targetid);
insert into automatededge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Automated');

drop view child;
create table childedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Child')
);
create unique index on childedge(sourceid, targetid);
create index on childedge(sourceid);
create index on childedge(targetid);
insert into childedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Child');

drop view property;
create table labeledpropertyedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'LabeledProperty')
);
create unique index on labeledpropertyedge(sourceid, (data->>'key'), targetid); -- unique with key
create index on labeledpropertyedge(sourceid);
create index on labeledpropertyedge(targetid);
insert into labeledpropertyedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'LabeledProperty');

drop view derived;
create table derivedfromtemplateedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'DerivedFromTemplate')
);
create unique index on derivedfromtemplateedge(sourceid, targetid);
create index on derivedfromtemplateedge(sourceid);
create index on derivedfromtemplateedge(targetid);
insert into derivedfromtemplateedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'DerivedFromTemplate');

-- drop references;
create table referencestemplateedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'ReferencesTemplate')
);
create unique index on referencestemplateedge(sourceid, targetid);
create index on referencestemplateedge(sourceid);
create index on referencestemplateedge(targetid);
insert into referencestemplateedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'ReferencesTemplate');

-- drop view mention;
create table mentionedge (
    sourceid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    targetid uuid NOT NULL REFERENCES node ON DELETE CASCADE,
    data jsonb not null CHECK(data->>'type' = 'Mention')
);
create unique index on mentionedge(sourceid, targetid);
create index on mentionedge(sourceid);
create index on mentionedge(targetid);
insert into mentionedge (select sourceid as sourceid, targetid as targetid, data as data from edge where data->>'type' = 'Mention');

drop view useredge;
create view useredge as
    select * from readedge
    union all
    select * from mentionedge
    union all
    select * from assignededge
    union all
    select * from authoredge
    union all
    select * from expandededge
    union all
    select * from inviteedge
    union all
    select * from memberedge
    union all
    select * from pinnededge
    union all
    select * from notifyedge;

drop view contentedge;
create view contentedge as
    select * from childedge
    union all
    select * from labeledpropertyedge
    union all
    select * from automatededge
    union all
    select * from derivedfromtemplateedge
    union all
    select * from referencestemplateedge;

drop view accessedge;
create view accessedge as
    select * from childedge
    union all
    select * from labeledpropertyedge
    union all
    select * from automatededge;

create view alledge as
    select * from useredge
    union all
    select * from contentedge;


drop trigger edge_insert_trigger on edge;
drop function edge_insert;
drop trigger edge_update_trigger on edge;
drop function edge_update;
drop trigger edge_delete_trigger on edge;
drop function edge_delete;


drop table edge cascade;


create function childedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger childedge_insert_trigger before insert on childedge for each row execute procedure childedge_insert();
create function labeledpropertyedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger labeledpropertyedge_insert_trigger before insert on labeledpropertyedge for each row execute procedure labeledpropertyedge_insert();
create function memberedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
    return new;
  end;
$$;
create trigger memberedge_insert_trigger before insert on memberedge for each row execute procedure memberedge_insert();
create function childedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = any(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger childedge_update_trigger before insert on childedge for each row execute procedure childedge_update();
create function labeledpropertyedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = any(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger labeledpropertyedge_update_trigger before insert on labeledpropertyedge for each row execute procedure labeledpropertyedge_update();
create function memberedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
    return old;
  end;
$$;
create trigger memberedge_update_trigger before insert on memberedge for each row execute procedure memberedge_update();
create function childedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    return old;
  end;
$$;
create trigger childedge_delete_trigger before delete on childedge for each row execute procedure childedge_delete();
create function labeledpropertyedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    return old;
  end;
$$;
create trigger labeledpropertyedge_delete_trigger before delete on labeledpropertyedge for each row execute procedure labeledpropertyedge_delete();
create function memberedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.sourceid));
    return old;
  end;
$$;
create trigger memberedge_delete_trigger before delete on memberedge for each row execute procedure memberedge_delete();


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
 drop trigger childedge_insert_trigger on childedge;
 drop function childedge_insert;
 drop trigger childedge_update_trigger on childedge;
 drop function childedge_update;
 drop trigger childedge_delete_trigger on childedge;
 drop function childedge_delete;
 drop trigger labeledpropertyedge_insert_trigger on labeledpropertyedge;
 drop function labeledpropertyedge_insert;
 drop trigger labeledpropertyedge_update_trigger on labeledpropertyedge;
 drop function labeledpropertyedge_update;
 drop trigger labeledpropertyedge_delete_trigger on labeledpropertyedge;
 drop function labeledpropertyedge_delete;
 drop trigger memberedge_insert_trigger on memberedge;
 drop function memberedge_insert;
 drop trigger memberedge_update_trigger on memberedge;
 drop function memberedge_update;
 drop trigger memberedge_delete_trigger on memberedge;
 drop function memberedge_delete;

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

-- trigger for edge update
create function childedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger childedge_insert_trigger before insert on childedge for each row execute procedure childedge_insert();
create function labeledpropertyedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger labeledpropertyedge_insert_trigger before insert on labeledpropertyedge for each row execute procedure labeledpropertyedge_insert();
create function memberedge_insert() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
    return new;
  end;
$$;
create trigger memberedge_insert_trigger before insert on memberedge for each row execute procedure memberedge_insert();
create function childedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = any(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger childedge_update_trigger before insert on childedge for each row execute procedure childedge_update();
create function labeledpropertyedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = any(select node_can_access_deep_children(new.targetid));
    return new;
  end;
$$;
create trigger labeledpropertyedge_update_trigger before insert on labeledpropertyedge for each row execute procedure labeledpropertyedge_update();
create function memberedge_update() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.sourceid));
    return old;
  end;
$$;
create trigger memberedge_update_trigger before insert on memberedge for each row execute procedure memberedge_update();
create function childedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    return old;
  end;
$$;
create trigger childedge_delete_trigger before delete on childedge for each row execute procedure childedge_delete();
create function labeledpropertyedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.targetid));
    return old;
  end;
$$;
create trigger labeledpropertyedge_delete_trigger before delete on labeledpropertyedge for each row execute procedure labeledpropertyedge_delete();
create function memberedge_delete() returns trigger
  security definer
  language plpgsql
as $$
  begin
    --TODO: strictly speaking we just need to recalculate the user of this membership for this node.
    delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(old.sourceid));
    return old;
  end;
$$;
create trigger memberedge_delete_trigger before delete on memberedge for each row execute procedure memberedge_delete();

-- deep access children of node
create function node_can_access_deep_children(node_id uuid) returns table(id uuid) as $$
    with recursive
        content(id) AS (
            select id from node where id = node_id
            union -- discards duplicates, therefore handles cycles and diamond cases
            -- TODO: can_access_deep_children must only be invalidated when they inherit members
            select accessedge.targetid FROM content INNER JOIN accessedge ON accessedge.sourceid = content.id
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
        IF (exists(select 1 from node where id = node_id and (accesslevel IS NULL or accesslevel = 'readwrite'))) THEN -- null means inherit for the node, readwrite/public inherits as well

            -- recursively inherit permissions from parents. run all ensure_recursive_node_can_access
            -- intersect the uncachable_node_ids with the visited array. We can start caching as soon as there are no uncachable_node_ids from the visited array.
            uncachable_node_ids := (select array(
                select unnest(ensure_recursive_node_can_access(accessedge.sourceid, visited || node_id)) from accessedge where accessedge.targetid = node_id and accessedge.sourceid <> node_id
                intersect
                select unnest(visited)
            ));

            -- if there are not uncachable_node_ids, we can create complete records that can be used without needing calculation.
            -- if there are uncachable_node_idsa, then we cannot say whether the result is complete or missing some users. therefore we create an incomplete record.
            -- incomplete records can be used in aggregation in our recursion, they are not wrong, but might be missing users.
            is_complete := cardinality(uncachable_node_ids) = 0;
            insert into node_can_access_mat (
                select node_id as nodeid, node_can_access_mat.userid as userid, is_complete as complete
                from accessedge
                inner join node_can_access_mat
                on node_can_access_mat.nodeid = accessedge.sourceid
                where accessedge.targetid = node_id and accessedge.sourceid <> node_id
                union
                select node_id as nodeid, memberedge.targetid as userid, is_complete as complete
                from memberedge
                where data->>'level' = 'readwrite' and memberedge.sourceid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = is_complete;
        ELSE
            insert into node_can_access_mat (
                select node_id as nodeid, memberedge.targetid as userid, true as complete
                from memberedge
                where data->>'level' = 'readwrite' and memberedge.sourceid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = true;
        END IF;
    end if;

    return uncachable_node_ids;
end;
$$ language plpgsql strict;



create function node_can_access_users(node_id uuid) returns table(userid uuid) as $$
begin
    perform ensure_recursive_node_can_access(node_id, array[]::uuid[]);

    return query select node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = node_id;
end;
$$ language plpgsql strict;

create function node_can_access_users_multiple(node_ids uuid[]) returns table(nodeid uuid, userid uuid) as $$
begin
    perform ensure_recursive_node_can_access(ids.id, array[]::uuid[]) from (select unnest(node_ids) id) ids;

    return query select node_can_access_mat.nodeid, node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = any(node_ids);
end;
$$ language plpgsql strict;

create function node_can_access(node_id uuid, user_id uuid) returns boolean as $$
declare
    cached_access boolean;
begin
    cached_access := (
        select bool_or(node_can_access_mat.userid = user_id)
        from node_can_access_mat
        where node_can_access_mat.nodeid = node_id and node_can_access_mat.complete
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
        select contentedge.targetid
            FROM content INNER JOIN contentedge ON contentedge.sourceid = content.id
            where node_can_access(contentedge.targetid, userid)
    ),
    transitive_parents(id) AS (
        select id from node where id = any(page_parents) and node_can_access(id, userid)
        union
        select contentedge.sourceid
            FROM transitive_parents INNER JOIN contentedge ON contentedge.targetid = transitive_parents.id
            where node_can_access(contentedge.sourceid, userid)
    )

    -- all transitive children
    select * from content
    union
    -- direct parents of content, useful to know tags of content nodes
    select contentedge.sourceid from content INNER JOIN contentedge ON contentedge.targetid = content.id where node_can_access(contentedge.sourceid, userid)
    union
    -- transitive parents describe the path/breadcrumbs to the page
    select * FROM transitive_parents;
$$ language sql strict;


create function user_bookmarks(userid uuid, now timestamp default now_utc()) returns setof uuid as $$
    with recursive bookmarks(id) as (
        -- all pinned/invited channels of the user
        select sourceid as id from (
            select sourceid from inviteedge where inviteedge.targetid = userid
            union
            select sourceid from pinnededge where pinnededge.targetid = userid
        ) invite_pinned where node_can_access(invite_pinned.sourceid, userid)
        union
        -- all transitive project children for sidebar
        select childedge.targetid as id FROM bookmarks
        INNER JOIN childedge ON childedge.sourceid = bookmarks.id
            and (childedge.data->>'deletedAt' is null or millis_to_timestamp(childedge.data->>'deletedAt') > now)
        INNER JOIN node on node.id = childedge.targetid
        where node.role->>'type' = 'Project' and node_can_access(childedge.targetid, userid)
    ),
    bookmarks_and_parents(id) as (
        select id from bookmarks
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select childedge.sourceid as id FROM bookmarks_and_parents
        INNER JOIN childedge ON childedge.targetid = bookmarks_and_parents.id
            and (childedge.data->>'deletedAt' is null or millis_to_timestamp(childedge.data->>'deletedAt') > now)
        where node_can_access(childedge.sourceid, userid)
    )
    select * from bookmarks_and_parents;
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
        select useredge.targetid as id from content_node_ids inner join useredge on useredge.sourceid = content_node_ids.id
        union
        select userid as id
    )

    ---- induced subgraph of all nodes without edges - what kind of node has no edges?

    -- all nodes
    select node.id, node.data, node.role, node.accesslevel, node.views, null::uuid, null::uuid, null::jsonb -- all node columns
    from all_node_ids
    inner join node on node.id = all_node_ids.id

    union all

    -- induced edges
    select null, null, null, null, null, alledge.sourceid, alledge.targetid, alledge.data -- all alledge columns
    from all_node_ids
    inner join alledge on alledge.sourceid = all_node_ids.id
    and exists (select 1 from all_node_ids where all_node_ids.id = alledge.targetid)
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
        notifyedge.sourceid as subscribed_node,
        node.id as inspected_node
    from node_can_access_users_multiple(startids) as node_access
    inner join node on node.id = node_access.nodeid
    left outer join notifyedge on notifyedge.sourceid = node.id and notifyedge.targetid = node_access.userid

    union

    select
        notified_users.initial_node as initial_node,     -- initial_nodes are propageted in each step
        notified_users.userid as userid,
        notifyedge.sourceid as subscribed_node,
        node.id as inspected_node
    from notified_users
    inner join childedge on childedge.targetid = notified_users.inspected_node
        and (childedge.data->>'deletedAt' is null or millis_to_timestamp(childedge.data->>'deletedAt') > now)
    inner join node on node.id = childedge.sourceid
    left outer join notifyedge on notifyedge.sourceid = node.id and notifyedge.targetid = notified_users.userid
    where notified_users.subscribed_node is null and node_can_access(node.id, notified_users.userid)

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

