--
-- PostgreSQL database dump
--

-- Dumped from database version 11.5
-- Dumped by pg_dump version 11.5

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.userdetail DROP CONSTRAINT IF EXISTS userdetail_userid_fkey;
ALTER TABLE IF EXISTS ONLY public.usedfeature DROP CONSTRAINT IF EXISTS usedfeature_userid_fkey;
ALTER TABLE IF EXISTS ONLY public.oauthclient DROP CONSTRAINT IF EXISTS oauthclient_userid_fkey;
ALTER TABLE IF EXISTS ONLY public.node_can_access_mat DROP CONSTRAINT IF EXISTS node_can_access_mat_userid_fkey;
ALTER TABLE IF EXISTS ONLY public.node_can_access_mat DROP CONSTRAINT IF EXISTS node_can_access_mat_nodeid_fkey;
ALTER TABLE IF EXISTS ONLY public.webpushsubscription DROP CONSTRAINT IF EXISTS fk_user_post;
ALTER TABLE IF EXISTS ONLY public.password DROP CONSTRAINT IF EXISTS fk_user_post;
ALTER TABLE IF EXISTS ONLY public.edge DROP CONSTRAINT IF EXISTS connection_targetid_fkey;
ALTER TABLE IF EXISTS ONLY public.edge DROP CONSTRAINT IF EXISTS connection_sourceid_fkey;
DROP TRIGGER IF EXISTS node_update_trigger ON public.node;
DROP TRIGGER IF EXISTS edge_update_trigger ON public.edge;
DROP TRIGGER IF EXISTS edge_insert_trigger ON public.edge;
DROP TRIGGER IF EXISTS edge_delete_trigger ON public.edge;
DROP INDEX IF EXISTS public.webpushsubscription_endpointurl_p256dh_auth_idx;
DROP INDEX IF EXISTS public.unique_user_email;
DROP INDEX IF EXISTS public.rawpost_joinlevel_idx;
DROP INDEX IF EXISTS public.post_content_type;
DROP INDEX IF EXISTS public.oauthclient_service_idx;
DROP INDEX IF EXISTS public.node_expr_idx2;
DROP INDEX IF EXISTS public.node_expr_idx1;
DROP INDEX IF EXISTS public.node_can_access_mat_userid_idx;
DROP INDEX IF EXISTS public.node_can_access_mat_nodeid_userid_idx;
DROP INDEX IF EXISTS public.node_can_access_mat_complete_idx;
DROP INDEX IF EXISTS public.idx_edge_type;
DROP INDEX IF EXISTS public.idx_edge_targetid;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
DROP INDEX IF EXISTS public.edge_unique_index;
DROP INDEX IF EXISTS public.edge_index;
ALTER TABLE IF EXISTS ONLY public.webpushsubscription DROP CONSTRAINT IF EXISTS webpushsubscription_pkey;
ALTER TABLE IF EXISTS ONLY public.userdetail DROP CONSTRAINT IF EXISTS userdetail_pkey;
ALTER TABLE IF EXISTS ONLY public.usedfeature DROP CONSTRAINT IF EXISTS usedfeature_userid_feature_key;
ALTER TABLE IF EXISTS ONLY public.node DROP CONSTRAINT IF EXISTS post_pkey;
ALTER TABLE IF EXISTS ONLY public.password DROP CONSTRAINT IF EXISTS password_pkey;
ALTER TABLE IF EXISTS ONLY public.oauthclient DROP CONSTRAINT IF EXISTS oauthclient_userid_service_key;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS public.webpushsubscription ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.webpushsubscription_id_seq;
DROP TABLE IF EXISTS public.webpushsubscription;
DROP VIEW IF EXISTS public.usernode;
DROP VIEW IF EXISTS public.useredge;
DROP TABLE IF EXISTS public.userdetail;
DROP TABLE IF EXISTS public.usedfeature;
DROP VIEW IF EXISTS public.property;
DROP VIEW IF EXISTS public.pinned;
DROP TABLE IF EXISTS public.password;
DROP TABLE IF EXISTS public.oauthclient;
DROP VIEW IF EXISTS public.notify;
DROP TABLE IF EXISTS public.node_can_access_mat;
DROP TABLE IF EXISTS public.node;
DROP VIEW IF EXISTS public.member;
DROP VIEW IF EXISTS public.invite;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP VIEW IF EXISTS public.expanded;
DROP VIEW IF EXISTS public.derived;
DROP VIEW IF EXISTS public.contentedge;
DROP VIEW IF EXISTS public.child;
DROP VIEW IF EXISTS public.automated;
DROP VIEW IF EXISTS public.author;
DROP VIEW IF EXISTS public.assigned;
DROP VIEW IF EXISTS public.accessedge;
DROP TABLE IF EXISTS public.edge;
DROP AGGREGATE IF EXISTS public.array_merge_agg(anyarray);
DROP FUNCTION IF EXISTS public.user_bookmarks(userid uuid, now timestamp without time zone);
DROP FUNCTION IF EXISTS public.notified_users_by_nodeid(startids uuid[]);
DROP FUNCTION IF EXISTS public.notified_users_at_deepest_node(startids uuid[], now timestamp without time zone);
DROP FUNCTION IF EXISTS public.now_utc();
DROP FUNCTION IF EXISTS public.node_update();
DROP FUNCTION IF EXISTS public.node_can_access_users_multiple(node_ids uuid[]);
DROP FUNCTION IF EXISTS public.node_can_access_users(node_id uuid);
DROP FUNCTION IF EXISTS public.node_can_access_deep_children(node_id uuid);
DROP FUNCTION IF EXISTS public.node_can_access(node_id uuid, user_id uuid);
DROP FUNCTION IF EXISTS public.millis_to_timestamp(millis anyelement);
DROP FUNCTION IF EXISTS public.mergefirstuserintosecond(olduser uuid, keepuser uuid);
DROP FUNCTION IF EXISTS public.inaccessible_nodes(node_ids uuid[], user_id uuid);
DROP FUNCTION IF EXISTS public.graph_traversed_page_nodes(page_parents uuid[], userid uuid);
DROP FUNCTION IF EXISTS public.graph_page(parents uuid[], userid uuid);
DROP FUNCTION IF EXISTS public.ensure_recursive_node_can_access(node_id uuid, visited uuid[]);
DROP FUNCTION IF EXISTS public.edge_update();
DROP FUNCTION IF EXISTS public.edge_insert();
DROP FUNCTION IF EXISTS public.edge_delete();
DROP FUNCTION IF EXISTS public.array_merge(arr1 anyarray, arr2 anyarray);
DROP FUNCTION IF EXISTS public.array_intersect(anyarray, anyarray);
DROP TYPE IF EXISTS public.accesslevel;
--
-- Name: accesslevel; Type: TYPE; Schema: public; Owner: wust
--

CREATE TYPE public.accesslevel AS ENUM (
    'restricted',
    'readwrite'
);


ALTER TYPE public.accesslevel OWNER TO wust;

--
-- Name: array_intersect(anyarray, anyarray); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.array_intersect(anyarray, anyarray) RETURNS anyarray
    LANGUAGE sql IMMUTABLE
    AS $_$
    SELECT ARRAY(
        SELECT UNNEST($1)
        INTERSECT
        SELECT UNNEST($2)
    );
$_$;


ALTER FUNCTION public.array_intersect(anyarray, anyarray) OWNER TO wust;

--
-- Name: array_merge(anyarray, anyarray); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.array_merge(arr1 anyarray, arr2 anyarray) RETURNS anyarray
    LANGUAGE sql IMMUTABLE
    AS $$
    -- select array_agg(distinct elem order by elem)
    select array_agg(distinct elem)
    from (
        select unnest(arr1) elem
        union
        select unnest(arr2)
    ) s
$$;


ALTER FUNCTION public.array_merge(arr1 anyarray, arr2 anyarray) OWNER TO wust;

--
-- Name: edge_delete(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.edge_delete() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
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


ALTER FUNCTION public.edge_delete() OWNER TO wust;

--
-- Name: edge_insert(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.edge_insert() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
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


ALTER FUNCTION public.edge_insert() OWNER TO wust;

--
-- Name: edge_update(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.edge_update() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
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


ALTER FUNCTION public.edge_update() OWNER TO wust;

--
-- Name: ensure_recursive_node_can_access(uuid, uuid[]); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.ensure_recursive_node_can_access(node_id uuid, visited uuid[]) RETURNS uuid[]
    LANGUAGE plpgsql STRICT
    AS $$
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
                select unnest(ensure_recursive_node_can_access(accessedge.source_nodeid, visited || node_id)) from accessedge where accessedge.target_nodeid = node_id and accessedge.source_nodeid <> node_id
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
                on node_can_access_mat.nodeid = accessedge.source_nodeid
                where accessedge.target_nodeid = node_id and accessedge.source_nodeid <> node_id
                union
                select node_id as nodeid, member.target_userid as userid, is_complete as complete
                from member
                where data->>'level' = 'readwrite' and member.source_nodeid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = is_complete;
        ELSE
            insert into node_can_access_mat (
                select node_id as nodeid, member.target_userid as userid, true as complete
                from member
                where data->>'level' = 'readwrite' and member.source_nodeid = node_id
            ) on conflict (nodeid, userid) DO UPDATE set complete = true;
        END IF;
    end if;

    return uncachable_node_ids;
end;
$$;


ALTER FUNCTION public.ensure_recursive_node_can_access(node_id uuid, visited uuid[]) OWNER TO wust;

--
-- Name: graph_page(uuid[], uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.graph_page(parents uuid[], userid uuid) RETURNS TABLE(nodeid uuid, data jsonb, role jsonb, accesslevel public.accesslevel, views jsonb[], sourceid uuid, targetid uuid, edgedata jsonb)
    LANGUAGE sql STRICT
    AS $$
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
$$;


ALTER FUNCTION public.graph_page(parents uuid[], userid uuid) OWNER TO wust;

--
-- Name: graph_traversed_page_nodes(uuid[], uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.graph_traversed_page_nodes(page_parents uuid[], userid uuid) RETURNS SETOF uuid
    LANGUAGE sql STRICT
    AS $$
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
$$;


ALTER FUNCTION public.graph_traversed_page_nodes(page_parents uuid[], userid uuid) OWNER TO wust;

--
-- Name: inaccessible_nodes(uuid[], uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.inaccessible_nodes(node_ids uuid[], user_id uuid) RETURNS SETOF uuid
    LANGUAGE sql STRICT
    AS $$
    select ids.id from (select unnest(node_ids) id) ids where not node_can_access(ids.id, user_id);
$$;


ALTER FUNCTION public.inaccessible_nodes(node_ids uuid[], user_id uuid) OWNER TO wust;

--
-- Name: mergefirstuserintosecond(uuid, uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.mergefirstuserintosecond(olduser uuid, keepuser uuid) RETURNS void
    LANGUAGE plpgsql STRICT
    AS $$
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
$$;


ALTER FUNCTION public.mergefirstuserintosecond(olduser uuid, keepuser uuid) OWNER TO wust;

--
-- Name: millis_to_timestamp(anyelement); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.millis_to_timestamp(millis anyelement) RETURNS timestamp with time zone
    LANGUAGE sql IMMUTABLE
    AS $$
    select to_timestamp(millis::bigint / 1000)
$$;


ALTER FUNCTION public.millis_to_timestamp(millis anyelement) OWNER TO wust;

--
-- Name: node_can_access(uuid, uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.node_can_access(node_id uuid, user_id uuid) RETURNS boolean
    LANGUAGE plpgsql STRICT
    AS $$
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
$$;


ALTER FUNCTION public.node_can_access(node_id uuid, user_id uuid) OWNER TO wust;

--
-- Name: node_can_access_deep_children(uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.node_can_access_deep_children(node_id uuid) RETURNS TABLE(id uuid)
    LANGUAGE sql STABLE
    AS $$
    with recursive
        content(id) AS (
            select id from node where id = node_id
            union -- discards duplicates, therefore handles cycles and diamond cases
            -- TODO: can_access_deep_children must only be invalidated when they inherit members
            select accessedge.target_nodeid FROM content INNER JOIN accessedge ON accessedge.source_nodeid = content.id
        )
        -- all transitive children
        select id from content;
$$;


ALTER FUNCTION public.node_can_access_deep_children(node_id uuid) OWNER TO wust;

--
-- Name: node_can_access_users(uuid); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.node_can_access_users(node_id uuid) RETURNS TABLE(userid uuid)
    LANGUAGE plpgsql STRICT
    AS $$
begin
    perform ensure_recursive_node_can_access(node_id, array[]::uuid[]);

    return query select node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = node_id;
end;
$$;


ALTER FUNCTION public.node_can_access_users(node_id uuid) OWNER TO wust;

--
-- Name: node_can_access_users_multiple(uuid[]); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.node_can_access_users_multiple(node_ids uuid[]) RETURNS TABLE(nodeid uuid, userid uuid)
    LANGUAGE plpgsql STRICT
    AS $$
begin
    perform ensure_recursive_node_can_access(ids.id, array[]::uuid[]) from (select unnest(node_ids) id) ids;

    return query select node_can_access_mat.nodeid, node_can_access_mat.userid from node_can_access_mat where node_can_access_mat.nodeid = any(node_ids);
end;
$$;


ALTER FUNCTION public.node_can_access_users_multiple(node_ids uuid[]) OWNER TO wust;

--
-- Name: node_update(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.node_update() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
  begin
    IF ((new.accesslevel is null and old.accesslevel is not null) or (new.accesslevel is not null and old.accesslevel is null) or new.accesslevel <> old.accesslevel) THEN
        delete from node_can_access_mat where nodeid = ANY(select node_can_access_deep_children(new.id));
    end if;
    return new;
  end;
$$;


ALTER FUNCTION public.node_update() OWNER TO wust;

--
-- Name: now_utc(); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.now_utc() RETURNS timestamp without time zone
    LANGUAGE sql STABLE
    AS $$
    select NOW() AT TIME ZONE 'utc';
$$;


ALTER FUNCTION public.now_utc() OWNER TO wust;

--
-- Name: notified_users_at_deepest_node(uuid[], timestamp without time zone); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.notified_users_at_deepest_node(startids uuid[], now timestamp without time zone DEFAULT public.now_utc()) RETURNS TABLE(userid uuid, initial_nodes uuid[], subscribed_node uuid)
    LANGUAGE sql STRICT
    AS $$
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
    from node_can_access_users_multiple(startids) as node_access
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
    where notified_users.subscribed_node is null and node_can_access(node.id, notified_users.userid)

)
    select
        notification_result.userid,
        array_agg(notification_result.initial_node),
        notification_result.subscribed_node
    from notified_users notification_result
    where notification_result.subscribed_node is not null
    group by notification_result.userid, notification_result.subscribed_node;
$$;


ALTER FUNCTION public.notified_users_at_deepest_node(startids uuid[], now timestamp without time zone) OWNER TO wust;

--
-- Name: notified_users_by_nodeid(uuid[]); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.notified_users_by_nodeid(startids uuid[]) RETURNS TABLE(userid uuid, notifiednodes uuid[], subscribednodeid uuid, subscribednodecontent text)
    LANGUAGE sql STRICT
    AS $$
    select notifications.userid, notifications.initial_nodes as notifiedNodes, notifications.subscribed_node as subscribedNodeId, node.data->>'content' as subscribedNodeContent
        from notified_users_at_deepest_node(startids) as notifications
        inner join node on node.id = notifications.subscribed_node
$$;


ALTER FUNCTION public.notified_users_by_nodeid(startids uuid[]) OWNER TO wust;

--
-- Name: user_bookmarks(uuid, timestamp without time zone); Type: FUNCTION; Schema: public; Owner: wust
--

CREATE FUNCTION public.user_bookmarks(userid uuid, now timestamp without time zone DEFAULT public.now_utc()) RETURNS SETOF uuid
    LANGUAGE sql STRICT
    AS $$
    with recursive bookmarks(id) as (
        -- all pinned/invited channels of the user
        select sourceid as id from edge where edge.targetid = userid and data->>'type' = any(array['Invite', 'Pinned']) and node_can_access(sourceid, userid)
        union
        -- all transitive project children for sidebar
        select child.target_childid as id FROM bookmarks
        INNER JOIN child ON child.source_parentid = bookmarks.id
            and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
        INNER JOIN node on node.id = child.target_childid
        where node.role->>'type' = 'Project' and node_can_access(child.target_childid, userid)
    ),
    bookmarks_and_parents(id) as (
        select id from bookmarks
        union
        -- all transitive parents of each channel. This is needed to correctly calculate the topological minor in the channel tree
        select child.source_parentid as id FROM bookmarks_and_parents
        INNER JOIN child ON child.target_childid = bookmarks_and_parents.id
            and (child.data->>'deletedAt' is null or millis_to_timestamp(child.data->>'deletedAt') > now)
        where node_can_access(child.source_parentid, userid)
    )
    select * from bookmarks_and_parents;
$$;


ALTER FUNCTION public.user_bookmarks(userid uuid, now timestamp without time zone) OWNER TO wust;

--
-- Name: array_merge_agg(anyarray); Type: AGGREGATE; Schema: public; Owner: wust
--

CREATE AGGREGATE public.array_merge_agg(anyarray) (
    SFUNC = public.array_merge,
    STYPE = anyarray
);


ALTER AGGREGATE public.array_merge_agg(anyarray) OWNER TO wust;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: edge; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.edge (
    sourceid uuid NOT NULL,
    targetid uuid NOT NULL,
    data jsonb NOT NULL
);


ALTER TABLE public.edge OWNER TO wust;

--
-- Name: accessedge; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.accessedge AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_nodeid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = ANY (ARRAY['Child'::text, 'LabeledProperty'::text, 'Automated'::text]));


ALTER TABLE public.accessedge OWNER TO wust;

--
-- Name: assigned; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.assigned AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Assigned'::text);


ALTER TABLE public.assigned OWNER TO wust;

--
-- Name: author; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.author AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Author'::text);


ALTER TABLE public.author OWNER TO wust;

--
-- Name: automated; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.automated AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_nodeid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Automated'::text);


ALTER TABLE public.automated OWNER TO wust;

--
-- Name: child; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.child AS
 SELECT edge.sourceid AS source_parentid,
    edge.targetid AS target_childid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Child'::text);


ALTER TABLE public.child OWNER TO wust;

--
-- Name: contentedge; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.contentedge AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_nodeid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = ANY (ARRAY['Child'::text, 'LabeledProperty'::text, 'DerivedFromTemplate'::text, 'Automated'::text, 'ReferencesTemplate'::text]));


ALTER TABLE public.contentedge OWNER TO wust;

--
-- Name: derived; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.derived AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_nodeid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'DerivedFromTemplate'::text);


ALTER TABLE public.derived OWNER TO wust;

--
-- Name: expanded; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.expanded AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Expanded'::text);


ALTER TABLE public.expanded OWNER TO wust;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO wust;

--
-- Name: invite; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.invite AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Invite'::text);


ALTER TABLE public.invite OWNER TO wust;

--
-- Name: member; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.member AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Member'::text);


ALTER TABLE public.member OWNER TO wust;

--
-- Name: node; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.node (
    id uuid NOT NULL,
    data jsonb NOT NULL,
    accesslevel public.accesslevel,
    role jsonb DEFAULT '{"type": "Message"}'::jsonb,
    views jsonb[]
);


ALTER TABLE public.node OWNER TO wust;

--
-- Name: node_can_access_mat; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.node_can_access_mat (
    nodeid uuid NOT NULL,
    userid uuid NOT NULL,
    complete boolean NOT NULL
);


ALTER TABLE public.node_can_access_mat OWNER TO wust;

--
-- Name: notify; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.notify AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Notify'::text);


ALTER TABLE public.notify OWNER TO wust;

--
-- Name: oauthclient; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.oauthclient (
    userid uuid NOT NULL,
    service text NOT NULL,
    accesstoken text NOT NULL
);


ALTER TABLE public.oauthclient OWNER TO wust;

--
-- Name: password; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.password (
    userid uuid NOT NULL,
    digest bytea NOT NULL
);


ALTER TABLE public.password OWNER TO wust;

--
-- Name: pinned; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.pinned AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'Pinned'::text);


ALTER TABLE public.pinned OWNER TO wust;

--
-- Name: property; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.property AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_nodeid,
    edge.data
   FROM public.edge
  WHERE ((edge.data ->> 'type'::text) = 'LabeledProperty'::text);


ALTER TABLE public.property OWNER TO wust;

--
-- Name: usedfeature; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.usedfeature (
    userid uuid NOT NULL,
    feature jsonb NOT NULL,
    "timestamp" timestamp without time zone NOT NULL
);


ALTER TABLE public.usedfeature OWNER TO wust;

--
-- Name: userdetail; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.userdetail (
    userid uuid NOT NULL,
    email text,
    verified boolean NOT NULL
);


ALTER TABLE public.userdetail OWNER TO wust;

--
-- Name: useredge; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.useredge AS
 SELECT edge.sourceid AS source_nodeid,
    edge.targetid AS target_userid,
    edge.data
   FROM public.edge
  WHERE (NOT ((edge.data ->> 'type'::text) = ANY (ARRAY['Child'::text, 'LabeledProperty'::text, 'DerivedFromTemplate'::text, 'Automated'::text, 'ReferencesTemplate'::text])));


ALTER TABLE public.useredge OWNER TO wust;

--
-- Name: usernode; Type: VIEW; Schema: public; Owner: wust
--

CREATE VIEW public.usernode AS
 SELECT node.id,
    node.data,
    node.accesslevel,
    node.role
   FROM public.node
  WHERE ((node.data ->> 'type'::text) = 'User'::text);


ALTER TABLE public.usernode OWNER TO wust;

--
-- Name: webpushsubscription; Type: TABLE; Schema: public; Owner: wust
--

CREATE TABLE public.webpushsubscription (
    id integer NOT NULL,
    userid uuid NOT NULL,
    endpointurl text NOT NULL,
    p256dh text NOT NULL,
    auth text NOT NULL
);


ALTER TABLE public.webpushsubscription OWNER TO wust;

--
-- Name: webpushsubscription_id_seq; Type: SEQUENCE; Schema: public; Owner: wust
--

CREATE SEQUENCE public.webpushsubscription_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.webpushsubscription_id_seq OWNER TO wust;

--
-- Name: webpushsubscription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: wust
--

ALTER SEQUENCE public.webpushsubscription_id_seq OWNED BY public.webpushsubscription.id;


--
-- Name: webpushsubscription id; Type: DEFAULT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.webpushsubscription ALTER COLUMN id SET DEFAULT nextval('public.webpushsubscription_id_seq'::regclass);


--
-- Data for Name: edge; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.edge (sourceid, targetid, data) FROM stdin;
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562866481423}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562866835627}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562866497035}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867368966}
246dedc6-243a-0a00-0beb-c4d86329b3b4	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Notify"}
246dedc9-7a4a-3d00-0beb-c52921a63779	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Notify"}
246dedca-929b-2d00-0beb-c3cb96965ce3	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246dedcb-974c-d900-0beb-c4e3a7d0dff6	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246dedcc-c055-d000-0beb-c4dbf20d81d0	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Notify"}
246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Notify"}
246dedcf-0d99-8e00-0beb-c4352b0feb3a	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Notify"}
246dedd0-2236-3900-0beb-c3131fbba3b8	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Notify"}
246dedd1-194a-5d00-0beb-c5195068e4cc	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Notify"}
246dedd2-3468-e900-0beb-c4797c30fedf	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562867637298}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Read", "timestamp": 1562867648254}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Read", "timestamp": 1562867649533}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Read", "timestamp": 1562867713542}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Read", "timestamp": 1562867751944}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867368966}
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867381294}
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867381294}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def1b-8fe8-4501-0beb-c468922ff921	{"type": "Child", "ordering": 1562867381285.030448800000011993851009313, "deletedAt": null}
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867390303}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867390303}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def1f-16b9-bf02-0beb-c3f1824d4e88	{"type": "Child", "ordering": 1562867390303.03044880000002148248338804, "deletedAt": null}
246def22-4558-2803-0beb-c3cd98c524d9	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867398441}
246def22-4558-2803-0beb-c3cd98c524d9	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867398441}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def22-4558-2803-0beb-c3cd98c524d9	{"type": "Child", "ordering": 1562867398440.030448800000031328241517785, "deletedAt": null}
246def24-b940-a804-0beb-c31ab1abee87	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867404711}
246def24-b940-a804-0beb-c31ab1abee87	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867404711}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def24-b940-a804-0beb-c31ab1abee87	{"type": "Child", "ordering": 1562867404712.030448800000040559860149895, "deletedAt": null}
246def27-4148-9105-0beb-c4170f451769	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867411182}
246def27-4148-9105-0beb-c4170f451769	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867411182}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def27-4148-9105-0beb-c4170f451769	{"type": "Child", "ordering": 1562867411185.030448800000051643762227049, "deletedAt": null}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562867519969}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867421673}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867421673}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def2b-5b11-4706-0beb-c4777a1eae11	{"type": "Child", "ordering": 1562867421671.030448800000062057871732241, "deletedAt": null}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867432575}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867432575}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246def2f-9ee6-2107-0beb-c2bd2be44a1a	{"type": "Child", "ordering": 1562867432577.030448800000070158183737882, "deletedAt": null}
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867458008}
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867458008}
246def1b-8fe8-4501-0beb-c468922ff921	246def39-9145-5c08-0beb-c2d1bfcb6705	{"type": "Child", "ordering": 1562867458012.030448800000080246564480773, "deletedAt": null}
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867466251}
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867466251}
246def1b-8fe8-4501-0beb-c468922ff921	246def3c-ca66-ce09-0beb-c4189521dfec	{"type": "Child", "ordering": 1562867466254.030448800000091650303033324, "deletedAt": null}
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867474005}
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867474005}
246def1b-8fe8-4501-0beb-c468922ff921	246def3f-d27a-160a-0beb-c4767631b609	{"type": "Child", "ordering": 1562867474006.030448800000102053510903305, "deletedAt": null}
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Author", "timestamp": 1562867484025}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Read", "timestamp": 1562867484025}
246def1b-8fe8-4501-0beb-c468922ff921	246def43-bdb5-5b0b-0beb-c3568a4a0a45	{"type": "Child", "ordering": 1562867484027.030448800000110816897460805, "deletedAt": null}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Member", "level": "readwrite"}
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Notify"}
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	{"type": "Pinned"}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Read", "timestamp": 1562867652337}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562867676034}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562867583393}
246def22-4558-2803-0beb-c3cd98c524d9	246def6a-9639-8001-0beb-c2b7d2e661e4	{"type": "Child", "ordering": 1562867583360.03044880000001013521586634, "deletedAt": null}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562867591434}
246def22-4558-2803-0beb-c3cd98c524d9	246def6d-bc08-9102-0beb-c3b2aeeee6b1	{"type": "Child", "ordering": 1562867591409.030448800000021212649236145, "deletedAt": null}
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562867597998}
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562867597998}
246def22-4558-2803-0beb-c3cd98c524d9	246def70-4f60-8b03-0beb-c4e0e34528ce	{"type": "Child", "ordering": 1562867597995.03044880000003251060742779, "deletedAt": null}
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562867605406}
246def22-4558-2803-0beb-c3cd98c524d9	246def73-32cf-a504-0beb-c3a4ce0f0647	{"type": "Child", "ordering": 1562867605381.030448800000041153041892935, "deletedAt": null}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562867630640}
246def24-b940-a804-0beb-c31ab1abee87	246def7d-10f5-d605-0beb-c3cc4fdeea75	{"type": "Child", "ordering": 1562867630614.030448800000051322723502709, "deletedAt": null}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868889132}
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868911488}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868901924}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868955386}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869165009}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869002620}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869192586}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Read", "timestamp": 1562867688256}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Read", "timestamp": 1562867751936}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Member", "level": "readwrite"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246def24-b940-a804-0beb-c31ab1abee87	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Member", "level": "readwrite"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Member", "level": "readwrite"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Member", "level": "readwrite"}
246def27-4148-9105-0beb-c4170f451769	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Member", "level": "readwrite"}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Member", "level": "readwrite"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Member", "level": "readwrite"}
246def24-b940-a804-0beb-c31ab1abee87	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Notify"}
246def24-b940-a804-0beb-c31ab1abee87	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Pinned"}
246def24-b940-a804-0beb-c31ab1abee87	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Read", "timestamp": 1562867935678}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Notify"}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Pinned"}
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedc9-7a4a-3d00-0beb-c52921a63779	{"type": "Read", "timestamp": 1562867945027}
246def22-4558-2803-0beb-c3cd98c524d9	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562867947510}
246def22-4558-2803-0beb-c3cd98c524d9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246def22-4558-2803-0beb-c3cd98c524d9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562867949855}
246def24-b940-a804-0beb-c31ab1abee87	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Notify"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Pinned"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Read", "timestamp": 1562867951793}
246def24-b940-a804-0beb-c31ab1abee87	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Notify"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Pinned"}
246def24-b940-a804-0beb-c31ab1abee87	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Read", "timestamp": 1562867953869}
246def27-4148-9105-0beb-c4170f451769	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Notify"}
246def27-4148-9105-0beb-c4170f451769	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Pinned"}
246def27-4148-9105-0beb-c4170f451769	246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"type": "Read", "timestamp": 1562867956255}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Notify"}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Pinned"}
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd0-2236-3900-0beb-c3131fbba3b8	{"type": "Read", "timestamp": 1562867958444}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Notify"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Pinned"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd1-194a-5d00-0beb-c5195068e4cc	{"type": "Read", "timestamp": 1562867961274}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Member", "level": "readwrite"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Read", "timestamp": 1562868017613}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Notify"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Pinned"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562868027824}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562868031352}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562868036483}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Read", "timestamp": 1562868034126}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Notify"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Pinned"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Member", "level": "readwrite"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562868041336}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562868044393}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562868047272}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Member", "level": "readwrite"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Read", "timestamp": 1562868050561}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Notify"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Pinned"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Member", "level": "readwrite"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Read", "timestamp": 1562868055168}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Notify"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Pinned"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Member", "level": "readwrite"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Read", "timestamp": 1562868064347}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Notify"}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Pinned"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868133357}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868133376}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868133380}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868219329}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246df063-4821-c501-0beb-c30c29953475	{"type": "Child", "ordering": 1562868219301.030448800000010497447416949, "deletedAt": null}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868229140}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868299569}
246def1b-8fe8-4501-0beb-c468922ff921	246df082-abe5-3002-0beb-c47cf1ef8b08	{"type": "Child", "ordering": 1562868299568.03044880000002208135674548, "deletedAt": null}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868307203}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868493263}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868307218}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868493274}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868307225}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868493277}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868326966}
246df082-abe5-3002-0beb-c47cf1ef8b08	246df08d-6316-d903-0beb-c32b118ae54f	{"type": "Child", "ordering": 1562868326969.030448800000030630188074319, "deletedAt": null}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868342691}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868342709}
246df09c-af4e-6705-0beb-c37d90e281e1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868366084}
246df09c-af4e-6705-0beb-c37d90e281e1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868366084}
246df08d-6316-d903-0beb-c32b118ae54f	246df09c-af4e-6705-0beb-c37d90e281e1	{"key": "Due Date", "type": "LabeledProperty", "showOnCard": true}
246df09c-af4e-6705-0beb-c37d90e281e1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868456185}
246df082-abe5-3002-0beb-c47cf1ef8b08	246df0bf-eb61-3a06-0beb-c42d83cf6eb6	{"type": "Child", "ordering": 1562868456186.030448800000061740206730934, "deletedAt": null}
246df0c5-a8ef-f108-0beb-c31717c4359a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868470860}
246df0c5-a8ef-f108-0beb-c31717c4359a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868470860}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246df0c5-a8ef-f108-0beb-c31717c4359a	{"key": "Due Date", "type": "LabeledProperty", "showOnCard": true}
246df0c5-a8ef-f108-0beb-c31717c4359a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868505169}
246def3f-d27a-160a-0beb-c4767631b609	246df0d3-1369-3309-0beb-c2bf5bd0e8ae	{"type": "Child", "ordering": 1562868505171.030448800000090167577708718, "deletedAt": null}
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868508235}
246def3f-d27a-160a-0beb-c4767631b609	246df0d4-468e-af0a-0beb-c32aaa58952c	{"type": "Child", "ordering": 1562868508239.030448800000100628456723756, "deletedAt": null}
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868512886}
246def3f-d27a-160a-0beb-c4767631b609	246df0d6-17fb-780b-0beb-c4873e10525b	{"type": "Child", "ordering": 1562868512888.030448800000112125583635035, "deletedAt": null}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868553041}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868553049}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868553051}
246df0e9-03d8-780c-0beb-c51f81463c9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868561268}
246df0e9-03d8-780c-0beb-c51f81463c9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868561268}
246def24-b940-a804-0beb-c31ab1abee87	246df0e9-03d8-780c-0beb-c51f81463c9d	{"type": "Child", "ordering": 1562868561272.030448800000122779546270877, "deletedAt": null}
246df0e9-03d8-780c-0beb-c51f81463c9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0e9-ed9b-f70d-0beb-c3962616de23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868563602}
246df0e9-ed9b-f70d-0beb-c3962616de23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868563602}
246def24-b940-a804-0beb-c31ab1abee87	246df0e9-ed9b-f70d-0beb-c3962616de23	{"type": "Child", "ordering": 1562868563607.030448800000131090094292515, "deletedAt": null}
246df0e9-ed9b-f70d-0beb-c3962616de23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868569474}
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868569474}
246def24-b940-a804-0beb-c31ab1abee87	246df0ec-3945-a50e-0beb-c4518b8ef3b9	{"type": "Child", "ordering": 1562868569477.030448800000141894955545529, "deletedAt": null}
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868594021}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868594040}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868594051}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562868594051}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869337102}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869357972}
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869371436}
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869378038}
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869481332}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869491669}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868601409}
246def6a-9639-8001-0beb-c2b7d2e661e4	246df0f8-b5e0-9f0f-0beb-c361d12451f9	{"type": "Child", "ordering": 1562868601407.030448800000150865330811385, "deletedAt": null}
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868604726}
246def6a-9639-8001-0beb-c2b7d2e661e4	246df0fa-0226-f610-0beb-c3a35b86f228	{"type": "Child", "ordering": 1562868604726.030448800000161146825404968, "deletedAt": null}
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868612717}
246def6a-9639-8001-0beb-c2b7d2e661e4	246df0fd-2274-7011-0beb-c5026310961d	{"type": "Child", "ordering": 1562868612720.030448800000172654485386781, "deletedAt": null}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562868613918}
246def6a-9639-8001-0beb-c2b7d2e661e4	246df0fd-9ab0-c112-0beb-c2daba94f8cd	{"type": "Child", "ordering": 1562868613921.030448800000180285131733197, "deletedAt": null}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936083031}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246e580e-7a90-e902-3f11-245128873461	{"type": "Child", "ordering": 1562936083017.161087400000020828921164897, "deletedAt": null}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936086830}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246e580f-f932-9703-3f11-24ec645ceced	{"type": "Child", "ordering": 1562936086839.161087400000031495644957933, "deletedAt": null}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936295706}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936317793}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936386348}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936655128}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936655139}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936655140}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936783024}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936783030}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562936783030}
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936808889}
246e592c-6ced-e702-3f11-23a4149259fc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936814215}
246e592c-6ced-e702-3f11-23a4149259fc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562936814215}
246def3f-d27a-160a-0beb-c4767631b609	246e592c-6ced-e702-3f11-23a4149259fc	{"type": "Child", "ordering": 1562936814215.161087400000020085557008892, "deletedAt": null}
246e592c-6ced-e702-3f11-23a4149259fc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e593e-835a-1303-3f11-2403b117bcf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936860466}
246e593e-835a-1303-3f11-2403b117bcf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562936860466}
246def3f-d27a-160a-0beb-c4767631b609	246e593e-835a-1303-3f11-2403b117bcf7	{"type": "Child", "ordering": 1562936860467.161087400000030496204889335, "deletedAt": null}
246e593e-835a-1303-3f11-2403b117bcf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936941939}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246e595e-5fd9-f404-3f11-23f6e8cb8bd1	{"type": "Child", "ordering": 1562936941940.161087400000040441304845265, "deletedAt": null}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937150353}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936944107}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246e595f-38e5-6c05-3f11-24ce4fa8e56e	{"type": "Child", "ordering": 1562936944108.161087400000051366448596334, "deletedAt": null}
246e5960-e42d-9806-3f11-251c314abb60	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936948374}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246e5960-e42d-9806-3f11-251c314abb60	{"type": "Child", "ordering": 1562936948376.161087400000061700946557792, "deletedAt": null}
246e5960-e42d-9806-3f11-251c314abb60	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562936957745}
246e5960-e42d-9806-3f11-251c314abb60	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562936957745}
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942037655}
246e5960-e42d-9806-3f11-251c314abb60	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937132745}
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937468969}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937258535}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246e59da-2c18-a901-3f11-255316ece461	{"type": "Automated"}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246e59da-2c18-a901-3f11-255316ece461	{"type": "Child", "ordering": 1562937258505.161087400000011936727401569, "deletedAt": null}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937267587}
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937468969}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869264320}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869906704}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869913203}
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869944244}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869979743}
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870255650}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870263665}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937267594}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246e5a2c-7aa9-4d0c-3f11-260b798c2e30	{"type": "Child", "ordering": 1562937468973.16108740000012272865599032, "deletedAt": null}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937270417}
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937277291}
246e59da-2c18-a901-3f11-255316ece461	246e59e1-851e-2e02-3f11-23cc61231036	{"type": "Child", "ordering": 1562937277294.161087400000020258640252982, "deletedAt": null}
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937279819}
246e59da-2c18-a901-3f11-255316ece461	246e59e2-824d-af03-3f11-23a005bd4630	{"type": "Child", "ordering": 1562937279823.161087400000030068128294448, "deletedAt": null}
246e59f1-cc84-a905-3f11-255b12ce45d4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937318942}
246e59f1-cc84-a905-3f11-255b12ce45d4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937318942}
246e59da-2c18-a901-3f11-255316ece461	246e59f1-cc84-a905-3f11-255b12ce45d4	{"key": "Started", "type": "LabeledProperty", "showOnCard": false}
246e59f1-cc84-a905-3f11-255b12ce45d4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e59fc-6246-4406-3f11-259301538327	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937345998}
246e59fc-6246-4406-3f11-259301538327	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937345998}
246e59da-2c18-a901-3f11-255316ece461	246e59fc-6246-4406-3f11-259301538327	{"type": "Child", "ordering": 1562937345988.161087400000062211242935079, "deletedAt": null}
246e59fc-6246-4406-3f11-259301538327	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a10-92b4-2e07-3f11-241b79852233	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937397612}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246e5a10-92b4-2e07-3f11-241b79852233	{"type": "Automated"}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246e5a10-92b4-2e07-3f11-241b79852233	{"type": "Child", "ordering": 1562937397614.161087400000070598351749683, "deletedAt": null}
246e5a18-a522-6f09-3f11-250bca87bdec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937418252}
246e5a18-a522-6f09-3f11-250bca87bdec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937418252}
246e5a10-92b4-2e07-3f11-241b79852233	246e5a18-a522-6f09-3f11-250bca87bdec	{"key": "Testing", "type": "LabeledProperty", "showOnCard": false}
246e5a18-a522-6f09-3f11-250bca87bdec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937429257}
246e5a10-92b4-2e07-3f11-241b79852233	246e5a1c-f2c6-eb0a-3f11-23979f076ced	{"type": "Child", "ordering": 1562937429259.161087400000100032045362413, "deletedAt": null}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937463742}
246e5a10-92b4-2e07-3f11-241b79852233	246e5a2a-6f45-610b-3f11-249d1a2a7190	{"type": "Child", "ordering": 1562937463745.161087400000111155097719184, "deletedAt": null}
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a2d-7070-440d-3f11-2391a98a7349	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937471425}
246e5a2d-7070-440d-3f11-2391a98a7349	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937471425}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246e5a2d-7070-440d-3f11-2391a98a7349	{"type": "Child", "ordering": 1562937471428.161087400000130006451917641, "deletedAt": null}
246e5a2d-7070-440d-3f11-2391a98a7349	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937473576}
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937473576}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246e5a2e-47c8-0b0e-3f11-246cc4033b70	{"type": "Child", "ordering": 1562937473579.16108740000014094749387864, "deletedAt": null}
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937498299}
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937498299}
246e5960-e42d-9806-3f11-251c314abb60	246e5a37-f2c5-dd0f-3f11-25b0860e15c6	{"type": "Automated"}
246e5960-e42d-9806-3f11-251c314abb60	246e5a37-f2c5-dd0f-3f11-25b0860e15c6	{"type": "Child", "ordering": 1562937498301.161087400000152338023806406, "deletedAt": null}
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a40-02cd-0611-3f11-243db530640f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937518916}
246e5a40-02cd-0611-3f11-243db530640f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937518916}
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246e5a40-02cd-0611-3f11-243db530640f	{"key": "Deployed", "type": "LabeledProperty", "showOnCard": true}
246e5a40-02cd-0611-3f11-243db530640f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937572712}
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246e5a55-0c93-4b12-3f11-24b93a8e1e6c	{"type": "Child", "ordering": 1562937572715.1610874000001812759002067, "deletedAt": null}
246e59da-2c18-a901-3f11-255316ece461	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Member", "level": "readwrite"}
246e59da-2c18-a901-3f11-255316ece461	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Invite"}
246e59da-2c18-a901-3f11-255316ece461	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Member", "level": "readwrite"}
246e59da-2c18-a901-3f11-255316ece461	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Invite"}
246e59da-2c18-a901-3f11-255316ece461	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Member", "level": "readwrite"}
246e59da-2c18-a901-3f11-255316ece461	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Invite"}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937729443}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246e5a92-5692-5e13-3f11-2614592c761c	{"type": "Child", "ordering": 1562937729438.161087400000192766767552028, "deletedAt": null}
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937785233}
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870300008}
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a10-92b4-2e07-3f11-241b79852233	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870323817}
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870307663}
246e5a10-92b4-2e07-3f11-241b79852233	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870332086}
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870370013}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870340889}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def6a-9639-8001-0beb-c2b7d2e661e4	246e5aa8-28ac-1414-3f11-2406f6f2aac6	{"type": "Child", "ordering": 1562937785236.161087400000200510261766854, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937789980}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937842919}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246e5abe-b762-a616-3f11-259f98f05fd8	{"type": "Automated"}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246e5abe-b762-a616-3f11-259f98f05fd8	{"type": "Child", "ordering": 1562937842918.16108740000022226532618236, "deletedAt": null}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937847721}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937917016}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937847724}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937851543}
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937853962}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ac3-0955-2d17-3f11-2584d953ac42	{"type": "Child", "ordering": 1562937853965.161087400000232150442314818, "deletedAt": null}
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937855098}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ac3-7b29-3e18-3f11-23d89a57d139	{"type": "Child", "ordering": 1562937855102.161087400000240311139619129, "deletedAt": null}
246e5ac6-32f2-2419-3f11-2400a6083c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937862048}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ac6-32f2-2419-3f11-2400a6083c68	{"type": "Child", "ordering": 1562937862052.16108740000025048313442212, "deletedAt": null}
246e5ac8-3339-411a-3f11-24d6eff5be62	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937867166}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ac8-3339-411a-3f11-24d6eff5be62	{"type": "Child", "ordering": 1562937867169.161087400000261403497725538, "deletedAt": null}
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937908010}
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937908010}
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5adb-b1da-3b1d-3f11-261dea95c4f2	{"type": "Child", "ordering": 1562937917019.161087400000292807861855474, "deletedAt": null}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937927659}
246e5ae8-2b0d-d31f-3f11-260751da15f1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937948912}
246e5ae8-2b0d-d31f-3f11-260751da15f1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562937948912}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ae8-2b0d-d31f-3f11-260751da15f1	{"key": "Prepared", "type": "LabeledProperty", "showOnCard": false}
246e5ae8-2b0d-d31f-3f11-260751da15f1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5abe-b762-a616-3f11-259f98f05fd8	246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	{"key": "Budget (€)", "type": "LabeledProperty", "showOnCard": true}
246e5af8-7876-0a20-3f11-2513978b8f75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937990599}
246df0fa-0226-f610-0beb-c3a35b86f228	246e5af8-7876-0a20-3f11-2513978b8f75	{"type": "Automated"}
246df0fa-0226-f610-0beb-c3a35b86f228	246e5af8-7876-0a20-3f11-2513978b8f75	{"type": "Child", "ordering": 1562937990602.161087400000321664007376757, "deletedAt": null}
246e5afd-bb2e-1622-3f11-25cafbc898ed	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938004051}
246e5afd-bb2e-1622-3f11-25cafbc898ed	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938004051}
246e5af8-7876-0a20-3f11-2513978b8f75	246e5afd-bb2e-1622-3f11-25cafbc898ed	{"key": "Started", "type": "LabeledProperty", "showOnCard": false}
246e5afd-bb2e-1622-3f11-25cafbc898ed	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938031388}
246e5af8-7876-0a20-3f11-2513978b8f75	246e5b08-68f6-a123-3f11-25f5adf1dced	{"type": "Child", "ordering": 1562938031361.161087400000352635045788909, "deletedAt": null}
246e5b08-68f6-a123-3f11-25f5adf1dced	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Assigned"}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938088812}
246e5af8-7876-0a20-3f11-2513978b8f75	246e5b1e-e0f3-7024-3f11-24efbdc303f4	{"type": "Child", "ordering": 1562938088816.161087400000361510029722612, "deletedAt": null}
246e5b23-5d72-4025-3f11-2477952948a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938100286}
246df0fd-2274-7011-0beb-c5026310961d	246e5b23-5d72-4025-3f11-2477952948a6	{"type": "Automated"}
246df0fd-2274-7011-0beb-c5026310961d	246e5b23-5d72-4025-3f11-2477952948a6	{"type": "Child", "ordering": 1562938100288.161087400000370993952483494, "deletedAt": null}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Assigned"}
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938140523}
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938140523}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869524212}
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870014252}
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b23-5d72-4025-3f11-2477952948a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870044411}
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870023482}
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b23-5d72-4025-3f11-2477952948a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5af8-7876-0a20-3f11-2513978b8f75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870089473}
246e5af8-7876-0a20-3f11-2513978b8f75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870102123}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870122615}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870134330}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5ac6-32f2-2419-3f11-2400a6083c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870145965}
246e5ac6-32f2-2419-3f11-2400a6083c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5ac8-3339-411a-3f11-24d6eff5be62	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870158300}
246e5ac8-3339-411a-3f11-24d6eff5be62	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870171875}
246e5b23-5d72-4025-3f11-2477952948a6	246e5b33-19ca-2e27-3f11-25db2be6f6fd	{"key": "Online", "type": "LabeledProperty", "showOnCard": true}
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938175276}
246e5b23-5d72-4025-3f11-2477952948a6	246e5b40-b0b6-ac28-3f11-24bbc9148469	{"type": "Child", "ordering": 1562938175276.161087400000401286881313897, "deletedAt": null}
246e5b44-be95-8b29-3f11-24e0646555ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938185639}
246e5b23-5d72-4025-3f11-2477952948a6	246e5b44-be95-8b29-3f11-24e0646555ce	{"type": "Child", "ordering": 1562938185643.161087400000411444105901518, "deletedAt": null}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Assigned"}
246e5b44-be95-8b29-3f11-24e0646555ce	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Assigned"}
246e5b6f-0e80-502a-3f11-249340305022	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938293893}
246e5b6f-0e80-502a-3f11-249340305022	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938293893}
246e5b6f-0e80-502a-3f11-249340305022	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b7a-005c-832b-3f11-23f35d51bc74	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938321823}
246e5b7a-005c-832b-3f11-23f35d51bc74	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938321823}
246e5b7a-005c-832b-3f11-23f35d51bc74	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938325147}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938325147}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	{"type": "Automated"}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	{"type": "Child", "ordering": 1562938325150.161087400000442545248644169, "deletedAt": null}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246e5b6f-0e80-502a-3f11-249340305022	{"type": "Child", "ordering": 1562938293840.16108740000042111278596509, "deletedAt": 1562938337301}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246e5b7a-005c-832b-3f11-23f35d51bc74	{"type": "Child", "ordering": 1562938321827.161087400000430426079927412, "deletedAt": 1562938337991}
246e5b91-c051-9e02-3f11-26196575e75b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938382553}
246e5b91-c051-9e02-3f11-26196575e75b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938382553}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5b91-c051-9e02-3f11-26196575e75b	{"key": "Team", "type": "LabeledProperty", "showOnCard": true}
246e5b91-c051-9e02-3f11-26196575e75b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5bae-0514-bc04-3f11-257ec75b65c1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938454858}
246e5bae-0514-bc04-3f11-257ec75b65c1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562938454858}
246e5bae-0514-bc04-3f11-257ec75b65c1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5cbd-06ba-fe05-3f11-24643fd81dd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939147874}
246e5cbd-06ba-fe05-3f11-24643fd81dd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939147874}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5cbd-06ba-fe05-3f11-24643fd81dd2	{"type": "Child", "ordering": 1562939147838.16108740000005091091672213, "deletedAt": null}
246e5cbd-06ba-fe05-3f11-24643fd81dd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5ccb-5cab-7006-3f11-2608c9423927	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939184493}
246e5ccb-5cab-7006-3f11-2608c9423927	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939184493}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5ccb-5cab-7006-3f11-2608c9423927	{"type": "Child", "ordering": 1562939184496.161087400000062717108418855, "deletedAt": null}
246e5ccb-5cab-7006-3f11-2608c9423927	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939269014}
246df0d4-468e-af0a-0beb-c32aaa58952c	246e5cec-6a03-5607-3f11-2400c923c1b0	{"type": "Automated"}
246df0d4-468e-af0a-0beb-c32aaa58952c	246e5cec-6a03-5607-3f11-2400c923c1b0	{"type": "Child", "ordering": 1562939269014.161087400000070483723428272, "deletedAt": null}
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939283194}
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939283194}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	{"type": "Child", "ordering": 1562939283197.161087400000081905188502909, "deletedAt": null}
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d05-bb05-d709-3f11-23bf6f593df9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939333747}
246e5d05-bb05-d709-3f11-23bf6f593df9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939333747}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d05-bb05-d709-3f11-23bf6f593df9	{"type": "Child", "ordering": 1562939333751.161087400000090203044109817, "deletedAt": null}
246e5d05-bb05-d709-3f11-23bf6f593df9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939353367}
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939353367}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d0d-6723-5a0a-3f11-24ac972fb4a9	{"type": "Child", "ordering": 1562939353370.161087400000101221619725481, "deletedAt": null}
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939399624}
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939399624}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d1f-7c8f-3c0c-3f11-24697d735ac2	{"key": "FirstDay", "type": "LabeledProperty", "showOnCard": false}
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e5d31-a500-9c0d-3f11-25b18d58d025	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939446073}
246e5d31-a500-9c0d-3f11-25b18d58d025	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939446073}
246e5d62-32b8-b90f-3f11-24715d4a6f48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939570198}
246e5d62-32b8-b90f-3f11-24715d4a6f48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939570198}
246df0d6-17fb-780b-0beb-c4873e10525b	246e5d31-a500-9c0d-3f11-25b18d58d025	{"type": "Automated"}
246df0d6-17fb-780b-0beb-c4873e10525b	246e5d31-a500-9c0d-3f11-25b18d58d025	{"type": "Child", "ordering": 1562939446044.161087400000132342441111589, "deletedAt": null}
246e5d31-a500-9c0d-3f11-25b18d58d025	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d3a-b785-9b0e-3f11-2424adf15a20	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939497451}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870073264}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d3a-b785-9b0e-3f11-2424adf15a20	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939469241}
246e5d31-a500-9c0d-3f11-25b18d58d025	246e5d3a-b785-9b0e-3f11-2424adf15a20	{"type": "Child", "ordering": 1562939469243.161087400000140637885962784, "deletedAt": null}
246e5d3a-b785-9b0e-3f11-2424adf15a20	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939497451}
246e5d3a-b785-9b0e-3f11-2424adf15a20	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939562978}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939562986}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939565783}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d62-32b8-b90f-3f11-24715d4a6f48	{"type": "Child", "ordering": 1562939570201.1610874000001509672453282, "deletedAt": null}
246e5d62-32b8-b90f-3f11-24715d4a6f48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d62-cfe6-1b10-3f11-24a4ed39a889	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939571767}
246e5d62-cfe6-1b10-3f11-24a4ed39a889	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939571767}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d62-cfe6-1b10-3f11-24a4ed39a889	{"type": "Child", "ordering": 1562939571771.161087400000161188703479945, "deletedAt": null}
246e5d62-cfe6-1b10-3f11-24a4ed39a889	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5d65-a21d-0911-3f11-248b41f6cd32	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939578982}
246e5d65-a21d-0911-3f11-248b41f6cd32	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939578982}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e5d65-a21d-0911-3f11-248b41f6cd32	{"type": "Child", "ordering": 1562939578985.161087400000171078456012082, "deletedAt": null}
246e5d65-a21d-0911-3f11-248b41f6cd32	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e5d93-e077-3312-3f11-23efb628d24f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939697232}
246e5d93-e077-3312-3f11-23efb628d24f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939697232}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5d93-e077-3312-3f11-23efb628d24f	{"type": "Child", "ordering": 1562939697235.161087400000180410390549071, "deletedAt": null}
246e5d93-e077-3312-3f11-23efb628d24f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5dbb-fdbf-8414-3f11-26088435221f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939799809}
246e5dbb-fdbf-8414-3f11-26088435221f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562939799809}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5dbb-fdbf-8414-3f11-26088435221f	{"key": "E-Mail", "type": "LabeledProperty", "showOnCard": true}
246e5dbb-fdbf-8414-3f11-26088435221f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246e5bae-0514-bc04-3f11-257ec75b65c1	{"key": "FullName", "type": "LabeledProperty", "showOnCard": true}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562941426706}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562941426724}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562941426724}
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6077-a142-6301-3f11-2399ab4d19b5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562941588880}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e6077-a142-6301-3f11-2399ab4d19b5	{"type": "Child", "ordering": 1562941588867.161087400000010040841189813, "deletedAt": null}
246e608f-5b4f-6302-3f11-24cf9a89be03	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562941649538}
246e5cec-6a03-5607-3f11-2400c923c1b0	246e608f-5b4f-6302-3f11-24cf9a89be03	{"type": "Child", "ordering": 1562941649539.161087400000021371999813123, "deletedAt": null}
246e608f-5b4f-6302-3f11-24cf9a89be03	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562941995191}
246e608f-5b4f-6302-3f11-24cf9a89be03	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562941995191}
246e608f-5b4f-6302-3f11-24cf9a89be03	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6077-a142-6301-3f11-2399ab4d19b5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942006680}
246e6077-a142-6301-3f11-2399ab4d19b5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942006680}
246e6077-a142-6301-3f11-2399ab4d19b5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6122-ea9d-b204-3f11-2554ab0b95d3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942026914}
246e6122-eab7-5305-3f11-24df71c6e1c2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942026914}
246e6122-eab7-5306-3f11-254480b90c6e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942026914}
246e592a-56d3-b001-3f11-256967ea9c5b	246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246e6122-ea84-1101-3f11-25d4add70289	246e5dbb-fdbf-8414-3f11-26088435221f	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246e6122-ea84-1102-3f11-252e6f15e681	246e5bae-0514-bc04-3f11-257ec75b65c1	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246e6122-ea84-1101-3f11-25d4add70289	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942048660}
246e6122-ea84-1101-3f11-25d4add70289	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942048660}
246e6122-ea84-1102-3f11-252e6f15e681	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942058096}
246e592a-56d3-b001-3f11-256967ea9c5b	246def24-b940-a804-0beb-c31ab1abee87	{"key": "Team", "type": "LabeledProperty", "showOnCard": true}
246e6137-64a0-db07-3f11-26062f9907e8	246e6122-eab7-5305-3f11-24df71c6e1c2	{"type": "Child", "ordering": 1562942026867.16108740000005144003543085, "deletedAt": null}
246e6122-ea9d-b204-3f11-2554ab0b95d3	246e5ccb-5cab-7006-3f11-2608c9423927	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246e6122-eab7-5305-3f11-24df71c6e1c2	246e5cbd-06ba-fe05-3f11-24643fd81dd2	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246e6122-eab7-5306-3f11-254480b90c6e	246e5d93-e077-3312-3f11-23efb628d24f	{"type": "DerivedFromTemplate", "timestamp": 1562942026857}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246e592a-56d3-b001-3f11-256967ea9c5b	{"type": "Child", "ordering": 1562936808880.161087400000012032575487067, "deletedAt": null}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6122-eab7-5306-3f11-254480b90c6e	{"type": "Child", "ordering": 1562939697235.161087400000180410390549071, "deletedAt": null}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6122-eab7-5305-3f11-24df71c6e1c2	{"type": "Child", "ordering": 1562939147838.16108740000005091091672213, "deletedAt": null}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6122-ea9d-b204-3f11-2554ab0b95d3	{"type": "Child", "ordering": 1562939184496.161087400000062717108418855, "deletedAt": null}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6122-ea84-1101-3f11-25d4add70289	{"key": "E-Mail", "type": "LabeledProperty", "showOnCard": true}
246e6122-ea84-1102-3f11-252e6f15e681	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938454858}
246e6122-eab7-5305-3f11-24df71c6e1c2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939147874}
246e6122-ea9d-b204-3f11-2554ab0b95d3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939184493}
246e6122-eab7-5306-3f11-254480b90c6e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939697232}
246e6122-ea84-1101-3f11-25d4add70289	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562939799809}
246def3f-d27a-160a-0beb-c4767631b609	246e592a-56d3-b001-3f11-256967ea9c5b	{"type": "Child", "ordering": 1562936808880.161087400000012032575487067, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246e617e-84c3-cf05-3f11-256a564f317c	{"type": "Child", "ordering": 1562937277294.161087400000020258640252982, "deletedAt": null}
246e6122-ea9d-b204-3f11-2554ab0b95d3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6122-eab7-5305-3f11-24df71c6e1c2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6122-eab7-5306-3f11-254480b90c6e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942037655}
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6122-ea84-1101-3f11-25d4add70289	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6122-ea84-1102-3f11-252e6f15e681	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942058096}
246e6122-ea84-1102-3f11-252e6f15e681	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6137-64a0-db07-3f11-26062f9907e8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942079230}
246e6137-64a0-db07-3f11-26062f9907e8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942079230}
246e6137-64a0-db07-3f11-26062f9907e8	246e6122-eab7-5306-3f11-254480b90c6e	{"type": "Child", "ordering": 1562942026867.161087400000061874077879406, "deletedAt": null}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6137-64a0-db07-3f11-26062f9907e8	{"type": "Child", "ordering": 1562942079227.161087400000072705940482024, "deletedAt": null}
246e6137-64a0-db07-3f11-26062f9907e8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": false}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942229642}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942237438}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942261122}
246e617e-84c3-cf02-3f11-243871f5d777	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942261122}
246e617e-84c3-cf03-3f11-259494710bd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942261122}
246e6172-349c-1101-3f11-23b5e165928e	246e59da-2c18-a901-3f11-255316ece461	{"type": "DerivedFromTemplate", "timestamp": 1562942261094}
246e617e-84c3-cf02-3f11-243871f5d777	246e59fc-6246-4406-3f11-259301538327	{"type": "DerivedFromTemplate", "timestamp": 1562942261094}
246e617e-84c3-cf03-3f11-259494710bd2	246e59f1-cc84-a905-3f11-255b12ce45d4	{"type": "DerivedFromTemplate", "timestamp": 1562942261094}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246e59e2-824d-af03-3f11-23a005bd4630	{"type": "DerivedFromTemplate", "timestamp": 1562942261094}
246e617e-84c3-cf05-3f11-256a564f317c	246e59e1-851e-2e02-3f11-23cc61231036	{"type": "DerivedFromTemplate", "timestamp": 1562942261094}
246e6172-349c-1101-3f11-23b5e165928e	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Invite"}
246e6172-349c-1101-3f11-23b5e165928e	246e617e-84c3-cf04-3f11-24ca856b8dc8	{"type": "Child", "ordering": 1562937279823.161087400000030068128294448, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246e617e-84c3-cf03-3f11-259494710bd2	{"key": "Started", "type": "LabeledProperty", "showOnCard": false}
246e6172-349c-1101-3f11-23b5e165928e	246e617e-84c3-cf02-3f11-243871f5d777	{"type": "Child", "ordering": 1562937345988.161087400000062211242935079, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Member", "level": "readwrite"}
246e6172-349c-1101-3f11-23b5e165928e	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"type": "Invite"}
246e6172-349c-1101-3f11-23b5e165928e	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Member", "level": "readwrite"}
246e6172-349c-1101-3f11-23b5e165928e	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Member", "level": "readwrite"}
246e6172-349c-1101-3f11-23b5e165928e	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Invite"}
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937277291}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937279819}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246e617e-84c3-cf02-3f11-243871f5d777	{"type": "Child", "ordering": 1562942261103.161087400000020722778969975, "deletedAt": null}
246e618a-9bdb-130e-3f11-240e34db8b23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942292070}
246e618a-9bdb-130e-3f11-240e34db8b23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942292070}
246e618a-9bdb-130e-3f11-240e34db8b23	246e617f-5fcf-db08-3f11-23e68e60bf48	{"type": "Child", "ordering": 1562942263291.161087400000080371068419912, "deletedAt": null}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246e618a-9bdb-130e-3f11-240e34db8b23	{"type": "Child", "ordering": 1562942292019.161087400000140541365209891, "deletedAt": null}
246e618a-9bdb-130e-3f11-240e34db8b23	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e618a-9bdb-130e-3f11-240e34db8b23	246e617f-5fcf-db07-3f11-25529d531bf5	{"type": "Child", "ordering": 1562942263291.161087400000071934687280117, "deletedAt": null}
246e617e-84c3-cf03-3f11-259494710bd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937318942}
246e617e-84c3-cf02-3f11-243871f5d777	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937345998}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246e6172-349c-1101-3f11-23b5e165928e	{"type": "Child", "ordering": 1562942229617.161087400000010162007847566, "deletedAt": null}
246e617e-84c3-cf02-3f11-243871f5d777	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617e-84c3-cf03-3f11-259494710bd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942263307}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e617f-5fcf-db07-3f11-25529d531bf5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e617f-5fcf-db08-3f11-23e68e60bf48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e617f-5fcf-db09-3f11-2502e35db6f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e617f-5fcf-db0a-3f11-23ad06880e1f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e617f-5fcf-db0b-3f11-2408b71a2684	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942263307}
246e6172-349c-1101-3f11-23b5e165928e	246e5a10-92b4-2e07-3f11-241b79852233	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246e5a2a-6f45-610b-3f11-249d1a2a7190	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e617f-5fcf-db07-3f11-25529d531bf5	246e5a2c-7aa9-4d0c-3f11-260b798c2e30	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565871215526}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870403139}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db08-3f11-23e68e60bf48	246e5a2e-47c8-0b0e-3f11-246cc4033b70	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e617f-5fcf-db09-3f11-2502e35db6f2	246e5a2d-7070-440d-3f11-2391a98a7349	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e617f-5fcf-db0a-3f11-23ad06880e1f	246e5a1c-f2c6-eb0a-3f11-23979f076ced	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e617f-5fcf-db0b-3f11-2408b71a2684	246e5a18-a522-6f09-3f11-250bca87bdec	{"type": "DerivedFromTemplate", "timestamp": 1562942263286}
246e6172-349c-1101-3f11-23b5e165928e	246e617f-5fcf-db0b-3f11-2408b71a2684	{"key": "Testing", "type": "LabeledProperty", "showOnCard": false}
246e6172-349c-1101-3f11-23b5e165928e	246e617f-5fcf-db0a-3f11-23ad06880e1f	{"type": "Child", "ordering": 1562937429259.161087400000100032045362413, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246e617f-5fcf-db06-3f11-25ea7e718c4d	{"type": "Child", "ordering": 1562937463745.161087400000111155097719184, "deletedAt": null}
246e617f-5fcf-db0b-3f11-2408b71a2684	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937418252}
246e617f-5fcf-db0a-3f11-23ad06880e1f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937429257}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246e617f-5fcf-db09-3f11-2502e35db6f2	{"type": "Child", "ordering": 1562937471428.161087400000130006451917641, "deletedAt": null}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246e617f-5fcf-db08-3f11-23e68e60bf48	{"type": "Child", "ordering": 1562937473579.16108740000014094749387864, "deletedAt": null}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246e617f-5fcf-db07-3f11-25529d531bf5	{"type": "Child", "ordering": 1562937468973.16108740000012272865599032, "deletedAt": null}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937463742}
246e617f-5fcf-db07-3f11-25529d531bf5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937468969}
246e617f-5fcf-db09-3f11-2502e35db6f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937471425}
246e617f-5fcf-db08-3f11-23e68e60bf48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937473576}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db07-3f11-25529d531bf5	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db08-3f11-23e68e60bf48	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db09-3f11-2502e35db6f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db0a-3f11-23ad06880e1f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-5fcf-db0b-3f11-2408b71a2684	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246e617f-f82f-0d0c-3f11-25234559f1f6	{"type": "Child", "ordering": 1562942264813.161087400000121731347870198, "deletedAt": null}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246e617f-5fcf-db06-3f11-25ea7e718c4d	{"type": "Child", "ordering": 1562942263291.161087400000062587004210253, "deletedAt": null}
246e617f-f82f-0d0c-3f11-25234559f1f6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942264818}
246e617f-f82f-0d0d-3f11-23d0fad077c8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942264818}
246e6172-349c-1101-3f11-23b5e165928e	246e5a37-f2c5-dd0f-3f11-25b0860e15c6	{"type": "DerivedFromTemplate", "timestamp": 1562942264808}
246e617f-f82f-0d0c-3f11-25234559f1f6	246e5a55-0c93-4b12-3f11-24b93a8e1e6c	{"type": "DerivedFromTemplate", "timestamp": 1562942264808}
246e617f-f82f-0d0d-3f11-23d0fad077c8	246e5a40-02cd-0611-3f11-243db530640f	{"type": "DerivedFromTemplate", "timestamp": 1562942264808}
246e5960-e42d-9806-3f11-251c314abb60	246e6172-349c-1101-3f11-23b5e165928e	{"type": "Child", "ordering": 1562942229617.161087400000010162007847566, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246e617f-f82f-0d0d-3f11-23d0fad077c8	{"key": "Deployed", "type": "LabeledProperty", "showOnCard": true}
246e6172-349c-1101-3f11-23b5e165928e	246e617f-f82f-0d0c-3f11-25234559f1f6	{"type": "Child", "ordering": 1562937572715.1610874000001812759002067, "deletedAt": null}
246e617f-f82f-0d0d-3f11-23d0fad077c8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937518916}
246e617f-f82f-0d0c-3f11-25234559f1f6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937572712}
246e617f-f82f-0d0c-3f11-25234559f1f6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617f-f82f-0d0d-3f11-23d0fad077c8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246e617f-5fcf-db0a-3f11-23ad06880e1f	{"type": "Child", "ordering": 1562942263291.161087400000100123976158751, "deletedAt": null}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": false}
246e618a-9bdb-130e-3f11-240e34db8b23	246e617f-5fcf-db09-3f11-2502e35db6f2	{"type": "Child", "ordering": 1562942263291.161087400000091592264996594, "deletedAt": null}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942329835}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942360322}
246e61a5-5044-3210-3f11-247569b3c4da	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942360322}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e5abe-b762-a616-3f11-259f98f05fd8	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-502a-910f-3f11-2423d7ac2bd1	246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-5044-3210-3f11-247569b3c4da	246e5ae8-2b0d-d31f-3f11-260751da15f1	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-5044-3211-3f11-2521cff85b61	246e5adb-b1da-3b1d-3f11-261dea95c4f2	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-5044-3212-3f11-25a04fd48f11	246e5ac8-3339-411a-3f11-24d6eff5be62	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-5044-3213-3f11-26087a823003	246e5ac6-32f2-2419-3f11-2400a6083c68	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-505d-d314-3f11-25160bc7db52	246e5ac3-7b29-3e18-3f11-23d89a57d139	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e61a5-505d-d315-3f11-2427f82d1a58	246e5ac3-0955-2d17-3f11-2584d953ac42	{"type": "DerivedFromTemplate", "timestamp": 1562942360296}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-505d-d315-3f11-2427f82d1a58	{"type": "Child", "ordering": 1562937853965.161087400000232150442314818, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-505d-d314-3f11-25160bc7db52	{"type": "Child", "ordering": 1562937855102.161087400000240311139619129, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-5044-3213-3f11-26087a823003	{"type": "Child", "ordering": 1562937862052.16108740000025048313442212, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-5044-3212-3f11-25a04fd48f11	{"type": "Child", "ordering": 1562937867169.161087400000261403497725538, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-5044-3211-3f11-2521cff85b61	{"type": "Child", "ordering": 1562937917019.161087400000292807861855474, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-5044-3210-3f11-247569b3c4da	{"key": "Prepared", "type": "LabeledProperty", "showOnCard": false}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61a5-502a-910f-3f11-2423d7ac2bd1	{"key": "Budget (€)", "type": "LabeledProperty", "showOnCard": true}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870475717}
246e61a5-5044-3212-3f11-25a04fd48f11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870768538}
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870776629}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937853962}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937855098}
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937862048}
246e61a5-5044-3212-3f11-25a04fd48f11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937867166}
246e61a5-502a-910f-3f11-2423d7ac2bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937908010}
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937927659}
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937917016}
246e61a5-5044-3210-3f11-247569b3c4da	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562937948912}
246def6a-9639-8001-0beb-c2b7d2e661e4	246e5aaa-03e8-7f15-3f11-24e5412142ac	{"type": "Child", "ordering": 1562937789983.161087400000211464989074092, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942392219}
246e61a5-5044-3210-3f11-247569b3c4da	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-502a-910f-3f11-2423d7ac2bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562942375868}
246e61a5-502a-910f-3f11-2423d7ac2bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942375868}
246e61a5-502a-910f-3f11-2423d7ac2bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d314-3f11-25160bc7db52	246e61a5-5044-3211-3f11-2521cff85b61	{"type": "Child", "ordering": 1562942360306.161087400000171725083573089, "deletedAt": null}
246e61a5-505d-d314-3f11-25160bc7db52	246e61a5-5044-3212-3f11-25a04fd48f11	{"type": "Child", "ordering": 1562942360306.161087400000182268394589969, "deletedAt": null}
246e61a5-505d-d314-3f11-25160bc7db52	246e61a5-5044-3213-3f11-26087a823003	{"type": "Child", "ordering": 1562942360306.161087400000192715787210755, "deletedAt": null}
246e61b1-ca5e-7318-3f11-23d6d1b63629	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942392219}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e5af8-7876-0a20-3f11-2513978b8f75	{"type": "DerivedFromTemplate", "timestamp": 1562942392206}
246e61b1-ca5e-7316-3f11-240f0836267b	246e5b1e-e0f3-7024-3f11-24efbdc303f4	{"type": "DerivedFromTemplate", "timestamp": 1562942392206}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246e5b08-68f6-a123-3f11-25f5adf1dced	{"type": "DerivedFromTemplate", "timestamp": 1562942392206}
246e61b1-ca5e-7318-3f11-23d6d1b63629	246e5afd-bb2e-1622-3f11-25cafbc898ed	{"type": "DerivedFromTemplate", "timestamp": 1562942392206}
246e61a5-505d-d314-3f11-25160bc7db52	246e61b1-ca5e-7317-3f11-24f6fd78d1d1	{"type": "Child", "ordering": 1562942392211.161087400000231541163373009, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b1-ca5e-7318-3f11-23d6d1b63629	{"key": "Started", "type": "LabeledProperty", "showOnCard": false}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b1-ca5e-7317-3f11-24f6fd78d1d1	{"type": "Child", "ordering": 1562938031361.161087400000352635045788909, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b1-ca5e-7316-3f11-240f0836267b	{"type": "Child", "ordering": 1562938088816.161087400000361510029722612, "deletedAt": null}
246e61b1-ca5e-7318-3f11-23d6d1b63629	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938004051}
246e61b4-c253-7a1a-3f11-251679c00cf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562942399805}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938031388}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Assigned"}
246e61b1-ca5e-7316-3f11-240f0836267b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938088812}
246e61b1-ca5e-7316-3f11-240f0836267b	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Assigned"}
246e61b1-ca5e-7318-3f11-23d6d1b63629	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d314-3f11-25160bc7db52	246e61b1-ca5e-7316-3f11-240f0836267b	{"type": "Child", "ordering": 1562942392211.161087400000220544911140475, "deletedAt": null}
246e61a5-505d-d314-3f11-25160bc7db52	246e61b4-c253-7a1b-3f11-24550f613706	{"type": "Child", "ordering": 1562942399802.16108740000027084567911399, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e5b23-5d72-4025-3f11-2477952948a6	{"type": "DerivedFromTemplate", "timestamp": 1562942399796}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246e5b40-b0b6-ac28-3f11-24bbc9148469	{"type": "DerivedFromTemplate", "timestamp": 1562942399796}
246e61b4-c253-7a1a-3f11-251679c00cf7	246e5b33-19ca-2e27-3f11-25db2be6f6fd	{"type": "DerivedFromTemplate", "timestamp": 1562942399796}
246e61b4-c253-7a1b-3f11-24550f613706	246e5b44-be95-8b29-3f11-24e0646555ce	{"type": "DerivedFromTemplate", "timestamp": 1562942399796}
246df0fd-2274-7011-0beb-c5026310961d	246e5aaa-03e8-7f15-3f11-24e5412142ac	{"type": "Child", "ordering": 1562937789983.161087400000211464989074092, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b4-c253-7a1b-3f11-24550f613706	{"type": "Child", "ordering": 1562938185643.161087400000411444105901518, "deletedAt": null}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b4-c253-7a1a-3f11-251679c00cf7	{"key": "Online", "type": "LabeledProperty", "showOnCard": true}
246e61b4-c253-7a1a-3f11-251679c00cf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938140523}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"type": "Assigned"}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938175276}
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562938185639}
246e61b4-c253-7a1b-3f11-24550f613706	246dedca-929b-2d00-0beb-c3cb96965ce3	{"type": "Assigned"}
246e61b4-c253-7a1a-3f11-251679c00cf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246e61b4-c253-7a19-3f11-24a0f85e1a3d	{"type": "Child", "ordering": 1562942399802.161087400000251171710548541, "deletedAt": null}
246e61a5-505d-d315-3f11-2427f82d1a58	246e61b4-c253-7a19-3f11-24a0f85e1a3d	{"type": "Child", "ordering": 1562942399802.161087400000251171710548541, "deletedAt": null}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870452049}
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870505510}
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-5044-3212-3f11-25a04fd48f11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870515201}
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61b1-ca5e-7316-3f11-240f0836267b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870811681}
246e61b1-ca5e-7316-3f11-240f0836267b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": false}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562943546446}
246def24-b940-a804-0beb-c31ab1abee87	246e6375-2ab2-9f01-3f11-24413ca87efd	{"type": "Child", "ordering": 1562943546431.161087400000010760539414269, "deletedAt": null}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562943569700}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562943569700}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944922832}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944927325}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944927348}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1562944927348}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944941472}
246e5a92-5692-5e13-3f11-2614592c761c	246e6596-b6d1-3101-3f11-24a80f73c202	{"type": "Child", "ordering": 1562944941457.161087400000011202162614786, "deletedAt": null}
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944946627}
246e5a92-5692-5e13-3f11-2614592c761c	246e6598-bc99-e502-3f11-23e4066f9d5c	{"type": "Child", "ordering": 1562944946629.1610874000000203601977583, "deletedAt": null}
246e65a2-092f-a803-3f11-25841a2243a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1562944970407}
246e5a92-5692-5e13-3f11-2614592c761c	246e65a2-092f-a803-3f11-25841a2243a6	{"type": "Child", "ordering": 1562944970408.161087400000032147234628518, "deletedAt": null}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119687412}
246f7091-3c0c-e801-3f11-496d471ea961	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119711214}
246f7091-3c0c-e801-3f11-496d471ea961	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119711214}
246e59da-2c18-a901-3f11-255316ece461	246f7091-3c0c-e801-3f11-496d471ea961	{"type": "Child", "ordering": 1563119711208.161088800000012136085014881, "deletedAt": null}
246f7091-3c0c-e801-3f11-496d471ea961	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f7092-6b17-9b02-3f11-47fed8991d27	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119714233}
246f7092-6b17-9b02-3f11-47fed8991d27	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119714233}
246e59da-2c18-a901-3f11-255316ece461	246f7092-6b17-9b02-3f11-47fed8991d27	{"type": "Child", "ordering": 1563119714235.161088800000020562272738599, "deletedAt": null}
246f7092-6b17-9b02-3f11-47fed8991d27	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f7093-9d89-b003-3f11-4836856dae24	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119717294}
246f7093-9d89-b003-3f11-4836856dae24	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119717294}
246f7093-9d89-b003-3f11-4836856dae24	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f7094-b3f3-ad04-3f11-4922ede189a3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119720074}
246f7094-b3f3-ad04-3f11-4922ede189a3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119720074}
246f7094-b3f3-ad04-3f11-4922ede189a3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f7091-3c0c-e801-3f11-496d471ea961	246f7093-9d89-b003-3f11-4836856dae24	{"type": "Child", "ordering": 1563119717296.16108880000003080139555178, "deletedAt": null}
246f7091-3c0c-e801-3f11-496d471ea961	246f7094-b3f3-ad04-3f11-4922ede189a3	{"type": "Child", "ordering": 1563119720077.161088800000041816760256931, "deletedAt": null}
246f70a0-5bea-db05-3f11-49cad99286bd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119749934}
246f70a0-5bea-db05-3f11-49cad99286bd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119749934}
246f7091-3c0c-e801-3f11-496d471ea961	246f70a0-5bea-db05-3f11-49cad99286bd	{"type": "Automated"}
246f7091-3c0c-e801-3f11-496d471ea961	246f70a0-5bea-db05-3f11-49cad99286bd	{"type": "Child", "ordering": 1563119749883.161088800000052537974040253, "deletedAt": null}
246f70a0-5bea-db05-3f11-49cad99286bd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f70a0-5bea-db05-3f11-49cad99286bd	246dedc6-243a-0a00-0beb-c4d86329b3b4	{"type": "Assigned"}
246e617f-5fcf-db06-3f11-25ea7e718c4d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": false}
246f70ed-2549-db06-3f11-48b8a016a56b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119946248}
246f70ed-2549-db06-3f11-48b8a016a56b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119946248}
246f70ed-2549-db06-3f11-48b8a016a56b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f7092-6b17-9b02-3f11-47fed8991d27	246f70ed-2549-db06-3f11-48b8a016a56b	{"type": "Child", "ordering": 1563119946235.161088800000061360188581227, "deletedAt": null}
246f70f1-9029-fb07-3f11-4991f55b98b6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119957529}
246f70f1-9029-fb07-3f11-4991f55b98b6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119957529}
246f7092-6b17-9b02-3f11-47fed8991d27	246f70f1-9029-fb07-3f11-4991f55b98b6	{"type": "Automated"}
246f7092-6b17-9b02-3f11-47fed8991d27	246f70f1-9029-fb07-3f11-4991f55b98b6	{"type": "Child", "ordering": 1563119957531.16108880000007229362706655, "deletedAt": null}
246f70f1-9029-fb07-3f11-4991f55b98b6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f70f1-9029-fb07-3f11-4991f55b98b6	246dedcc-c055-d000-0beb-c4dbf20d81d0	{"type": "Assigned"}
246f70fd-07f9-a808-3f11-47854803fecc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119986853}
246f70fd-07f9-a808-3f11-47854803fecc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119986853}
246e59da-2c18-a901-3f11-255316ece461	246f70fd-07f9-a808-3f11-47854803fecc	{"type": "Child", "ordering": 1563119986856.161088800000080040156004044, "deletedAt": null}
246f70fd-07f9-a808-3f11-47854803fecc	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246f70ff-74f9-c309-3f11-48dc20ca1c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563119993056}
246f70ff-74f9-c309-3f11-48dc20ca1c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563119993056}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869229866}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e65a2-092f-a803-3f11-25841a2243a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870216028}
246e65a2-092f-a803-3f11-25841a2243a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870233487}
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870245098}
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e59da-2c18-a901-3f11-255316ece461	246f70ff-74f9-c309-3f11-48dc20ca1c68	{"type": "Child", "ordering": 1563119993059.16108880000009151267168164, "deletedAt": null}
246f70ff-74f9-c309-3f11-48dc20ca1c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563201452457}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563201452467}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563201452468}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563201452468}
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563201459793}
246def22-4558-2803-0beb-c3cd98c524d9	246fed72-54b1-cc01-3f11-480f760fe40e	{"type": "Child", "ordering": 1563201459788.161088800000010633634022414, "deletedAt": null}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1563201471438}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1563201471438}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868873319}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868873448}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868873448}
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868889132}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868901865}
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868901924}
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868911488}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868955261}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868955386}
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868969585}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565868969690}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565868969690}
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869002564}
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869002620}
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869165009}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869192465}
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869192586}
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869205508}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869205508}
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869219616}
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869219616}
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869229866}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869243431}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869243496}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869320231}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869247830}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869336996}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869247916}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869247916}
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869264220}
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869264320}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869320127}
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869337102}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869357878}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869357972}
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869371364}
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869371436}
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869377954}
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869378038}
246e592a-56d3-b001-3f11-256967ea9c5b	246e6122-ea84-1102-3f11-252e6f15e681	{"key": "Voller Name", "type": "LabeledProperty", "showOnCard": true}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869481243}
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869481332}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869484872}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869484929}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869491609}
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869491669}
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869514226}
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565869514226}
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869524212}
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869906613}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869944244}
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869906704}
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869913133}
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869913203}
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869944175}
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869979669}
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565869979743}
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870014042}
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870014252}
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870023376}
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870023482}
246e5b23-5d72-4025-3f11-2477952948a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870044411}
246e5b44-be95-8b29-3f11-24e0646555ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870056394}
246e5b44-be95-8b29-3f11-24e0646555ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870056394}
246e5b44-be95-8b29-3f11-24e0646555ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870067237}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870067445}
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870073264}
246e5af8-7876-0a20-3f11-2513978b8f75	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870089473}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870102034}
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870102123}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870110967}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870111046}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870116953}
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870122615}
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870134330}
246e5ac6-32f2-2419-3f11-2400a6083c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870145965}
246e5ac8-3339-411a-3f11-24d6eff5be62	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870158300}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870171779}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870171875}
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e65a2-092f-a803-3f11-25841a2243a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870216028}
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870233340}
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870233487}
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870245006}
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870245098}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870255532}
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870255650}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870263586}
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870263665}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870293488}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870293488}
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870299890}
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870300008}
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870307571}
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870307663}
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e5a10-92b4-2e07-3f11-241b79852233	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870323817}
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870332086}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870340809}
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870340889}
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870370013}
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870395599}
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870395725}
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870395725}
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870402998}
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870403139}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870452049}
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870463855}
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870464084}
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870464084}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870475605}
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870475717}
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870505418}
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870505510}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870515126}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870515201}
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870523237}
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565870523310}
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870776629}
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870523310}
246e61a5-5044-3212-3f11-25a04fd48f11	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870768538}
246e61b1-ca5e-7316-3f11-240f0836267b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565870811681}
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871215526}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871283291}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871283409}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871334015}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871334284}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871341420}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871341534}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871500370}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871500642}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871507776}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871507887}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871776120}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565871776387}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565871776387}
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": false}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872794134}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Pinned"}
247fde31-70d8-ab02-4107-c801b34cf06c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872801511}
247fde31-70d8-ab02-4107-c801b34cf06c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872801511}
247fde2e-90ea-9401-4107-c68110e68c92	247fde31-70d8-ab02-4107-c801b34cf06c	{"type": "Child", "ordering": 1565872801483.166102400000022584008781932, "deletedAt": null}
247fde31-70d8-ab02-4107-c801b34cf06c	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde32-f0e1-2703-4107-c7e11e9accd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872805314}
247fde32-f0e1-2703-4107-c7e11e9accd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872805314}
247fde2e-90ea-9401-4107-c68110e68c92	247fde32-f0e1-2703-4107-c7e11e9accd1	{"type": "Child", "ordering": 1565872805319.166102400000032444075125969, "deletedAt": null}
247fde32-f0e1-2703-4107-c7e11e9accd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde34-76b8-1d04-4107-c5f2ad2add9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872809208}
247fde34-76b8-1d04-4107-c5f2ad2add9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872809208}
247fde2e-90ea-9401-4107-c68110e68c92	247fde34-76b8-1d04-4107-c5f2ad2add9d	{"type": "Child", "ordering": 1565872809213.166102400000040320458120605, "deletedAt": null}
247fde34-76b8-1d04-4107-c5f2ad2add9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde35-a442-6105-4107-c753300cd7a2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872812220}
247fde35-a442-6105-4107-c753300cd7a2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872812220}
247fde2e-90ea-9401-4107-c68110e68c92	247fde35-a442-6105-4107-c753300cd7a2	{"type": "Child", "ordering": 1565872812225.166102400000051834482456482, "deletedAt": null}
247fde35-a442-6105-4107-c753300cd7a2	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde4c-521b-8a06-4107-c61c216403e3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872870219}
247fde4c-521b-8a06-4107-c61c216403e3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872870219}
247fde2e-90ea-9401-4107-c68110e68c92	247fde4c-521b-8a06-4107-c61c216403e3	{"type": "Automated"}
247fde2e-90ea-9401-4107-c68110e68c92	247fde4c-521b-8a06-4107-c61c216403e3	{"type": "Child", "ordering": 1565872870218.166102400000060498501682147, "deletedAt": null}
247fde4c-521b-8a06-4107-c61c216403e3	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde56-c887-4c08-4107-c81b7d663fee	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872896967}
247fde56-c887-4c08-4107-c81b7d663fee	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872896967}
247fde56-c887-4c08-4107-c81b7d663fee	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde63-757d-090a-4107-c6c3456ebfd9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872929379}
247fde63-757d-090a-4107-c6c3456ebfd9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872929379}
247fde4c-521b-8a06-4107-c61c216403e3	247fde63-757d-090a-4107-c6c3456ebfd9	{"key": "Ansprechpartner", "type": "LabeledProperty", "showOnCard": true}
247fde63-757d-090a-4107-c6c3456ebfd9	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565872952174}
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565872952174}
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde84-68e7-0c12-4107-c7d0a05e4619	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873013659}
247fde84-68e7-0c12-4107-c7d0a05e4619	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873013659}
247fde4c-521b-8a06-4107-c61c216403e3	247fde84-68e7-0c12-4107-c7d0a05e4619	{"key": "Startzeitpunkt", "type": "LabeledProperty", "showOnCard": false}
247fde84-68e7-0c12-4107-c7d0a05e4619	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde8b-7971-3d14-4107-c5c08b0ecb05	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873031703}
247fde8b-7971-3d14-4107-c5c08b0ecb05	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873031703}
247fde4c-521b-8a06-4107-c61c216403e3	247fde8b-7971-3d14-4107-c5c08b0ecb05	{"key": "Endzeitpunkt", "type": "LabeledProperty", "showOnCard": false}
247fde8b-7971-3d14-4107-c5c08b0ecb05	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde4c-521b-8a06-4107-c61c216403e3	247fde56-c887-4c08-4107-c81b7d663fee	{"key": "Ort", "type": "LabeledProperty", "showOnCard": true}
247fdf80-e982-5902-4107-c5d25fc23e64	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873659332}
247fdf80-e982-5902-4107-c5d25fc23e64	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873659332}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf80-e982-5902-4107-c5d25fc23e64	{"key": "Unternehmen", "type": "LabeledProperty", "showOnCard": false}
247fdf80-e982-5902-4107-c5d25fc23e64	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fdf84-cb21-3e04-4107-c789ff03ff30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873669243}
247fdf84-cb21-3e04-4107-c789ff03ff30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873669243}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf84-cb21-3e04-4107-c789ff03ff30	{"key": "Plz", "type": "LabeledProperty", "showOnCard": false}
247fdf84-cb21-3e04-4107-c789ff03ff30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fdf89-b68c-e206-4107-c6a1b0b62a22	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873681825}
247fdf89-b68c-e206-4107-c6a1b0b62a22	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873681825}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf89-b68c-e206-4107-c6a1b0b62a22	{"key": "Stadt", "type": "LabeledProperty", "showOnCard": false}
247fdf89-b68c-e206-4107-c6a1b0b62a22	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fdf8e-e024-1308-4107-c73c89ce8113	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873695024}
247fdf8e-e024-1308-4107-c73c89ce8113	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873695024}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf8e-e024-1308-4107-c73c89ce8113	{"key": "Straße", "type": "LabeledProperty", "showOnCard": false}
247fdf8e-e024-1308-4107-c73c89ce8113	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fdf99-5629-510c-4107-c7319536f108	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873721774}
247fdf99-5629-510c-4107-c7319536f108	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873721774}
247fdf99-5629-510c-4107-c7319536f108	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873732472}
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873732472}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf9d-8511-ba0e-4107-c5bf9f2f9598	{"key": "Coach3", "type": "LabeledProperty", "showOnCard": false}
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf99-5629-510c-4107-c7319536f108	{"key": "Coach2", "type": "LabeledProperty", "showOnCard": false}
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874002220}
247fde31-70d8-ab02-4107-c801b34cf06c	247fe006-fe1f-840f-4107-c731ee179275	{"type": "Automated"}
247fde31-70d8-ab02-4107-c801b34cf06c	247fe006-fe1f-840f-4107-c731ee179275	{"type": "Child", "ordering": 1565874002180.166102400000151691641942645, "deletedAt": null}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874028950}
247fde31-70d8-ab02-4107-c801b34cf06c	247fe011-7658-9810-4107-c8227f4c6905	{"type": "Automated"}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874055905}
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875709355}
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874247102}
247fe011-7658-9810-4107-c8227f4c6905	247fe066-c5fa-7e12-4107-c7ee3ad94efa	{"type": "Child", "ordering": 1565874247102.166102400000182500383559418, "deletedAt": null}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874322679}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874322779}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874380464}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874380553}
247fdf96-79d5-de0a-4107-c6cea1e0d31e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565873714459}
247fdf96-79d5-de0a-4107-c6cea1e0d31e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565873714459}
247fde2e-90ea-9401-4107-c68110e68c92	247fdf96-79d5-de0a-4107-c6cea1e0d31e	{"key": "Coach1", "type": "LabeledProperty", "showOnCard": false}
247fdf96-79d5-de0a-4107-c6cea1e0d31e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874055987}
247fe011-7658-9810-4107-c8227f4c6905	247fe006-fe1f-840f-4107-c731ee179275	{"type": "ReferencesTemplate", "isCreate": true, "isRename": true}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874095951}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874095964}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874100236}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874104442}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874104447}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874107688}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874107722}
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874145163}
247fe011-7658-9810-4107-c8227f4c6905	247fe03e-e576-ec11-4107-c803db410fc0	{"type": "Child", "ordering": 1565874145132.166102400000172593269026752, "deletedAt": null}
247fe11a-805f-5f01-4107-c7122b2928ba	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874706700}
247fe11a-805f-5f01-4107-c7122b2928ba	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874706700}
247fe011-7658-9810-4107-c8227f4c6905	247fe11a-805f-5f01-4107-c7122b2928ba	{"type": "Child", "ordering": 1565874706687.16610240000001155522755193, "deletedAt": null}
247fe11a-805f-5f01-4107-c7122b2928ba	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe121-c144-5302-4107-c80d5cce3ad7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874725233}
247fe121-c144-5302-4107-c80d5cce3ad7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874725233}
247fe011-7658-9810-4107-c8227f4c6905	247fe121-c144-5302-4107-c80d5cce3ad7	{"type": "Child", "ordering": 1565874725235.166102400000022634097244887, "deletedAt": null}
247fe121-c144-5302-4107-c80d5cce3ad7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe132-8869-4a03-4107-c5d95c5d0ed7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874768137}
247fe132-8869-4a03-4107-c5d95c5d0ed7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874768137}
247fe011-7658-9810-4107-c8227f4c6905	247fe132-8869-4a03-4107-c5d95c5d0ed7	{"type": "Child", "ordering": 1565874768138.166102400000030211728273111, "deletedAt": null}
247fe132-8869-4a03-4107-c5d95c5d0ed7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe136-c956-e704-4107-c61ff8b187f7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874779013}
247fe136-c956-e704-4107-c61ff8b187f7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874779013}
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe136-c956-e704-4107-c61ff8b187f7	{"type": "Child", "ordering": 1565874779015.166102400000040514998765559, "deletedAt": null}
247fe136-c956-e704-4107-c61ff8b187f7	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe13c-d896-ce05-4107-c6a5d3682d1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874794508}
247fe13c-d896-ce05-4107-c6a5d3682d1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874794508}
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe13c-d896-ce05-4107-c6a5d3682d1a	{"type": "Child", "ordering": 1565874794510.166102400000051089898818842, "deletedAt": null}
247fe13c-d896-ce05-4107-c6a5d3682d1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe147-39fc-7e06-4107-c767e8d20152	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874821091}
247fe147-39fc-7e06-4107-c767e8d20152	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874821091}
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe147-39fc-7e06-4107-c767e8d20152	{"type": "Child", "ordering": 1565874821054.16610240000006192348173141, "deletedAt": null}
247fe147-39fc-7e06-4107-c767e8d20152	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe151-2559-b307-4107-c65929aaaf37	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874846417}
247fe151-2559-b307-4107-c65929aaaf37	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874846417}
247fe006-fe1f-840f-4107-c731ee179275	247fe151-2559-b307-4107-c65929aaaf37	{"type": "Child", "ordering": 1565874846419.166102400000070760633536311, "deletedAt": null}
247fe151-2559-b307-4107-c65929aaaf37	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe176-2dd7-dd08-4107-c78bba693725	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565874941115}
247fe176-2dd7-dd08-4107-c78bba693725	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565874941115}
247fde4c-521b-8a06-4107-c61c216403e3	247fe176-2dd7-dd08-4107-c78bba693725	{"type": "Child", "ordering": 1565874941117.166102400000082077321934629, "deletedAt": null}
247fe176-2dd7-dd08-4107-c78bba693725	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875002211}
247fde32-f0e1-2703-4107-c7e11e9accd1	247fe18e-0f70-4809-4107-c7edec97f3de	{"type": "Automated"}
247fde32-f0e1-2703-4107-c7e11e9accd1	247fe18e-0f70-4809-4107-c7edec97f3de	{"type": "Child", "ordering": 1565875002184.166102400000092499070653406, "deletedAt": null}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875026940}
247fe18e-0f70-4809-4107-c7edec97f3de	247fe197-be08-be0a-4107-c617e5830fe1	{"type": "Child", "ordering": 1565875026942.166102400000100480317214689, "deletedAt": null}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875039285}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875089463}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875039380}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875271975}
247fde32-f0e1-2703-4107-c7e11e9accd1	247fe1b0-2f97-870b-4107-c6435b305b30	{"type": "Automated"}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875105493}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875112469}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875105586}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875105586}
247fe1b0-2f97-870b-4107-c6435b305b30	247fe011-7658-9810-4107-c8227f4c6905	{"type": "ReferencesTemplate", "isCreate": false, "isRename": false}
247fde2e-90ea-9401-4107-c68110e68c92	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Pinned"}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875112573}
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875162746}
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875162746}
247fe1b0-2f97-870b-4107-c6435b305b30	247fe1cc-d9c4-ba0c-4107-c69bde8fb534	{"type": "Child", "ordering": 1565875162746.166102400000121047136286004, "deletedAt": null}
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875195590}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875204348}
247fde34-76b8-1d04-4107-c5f2ad2add9d	247fe1dd-1eaa-7c0d-4107-c7e05e391294	{"type": "Automated"}
247fde34-76b8-1d04-4107-c5f2ad2add9d	247fe1dd-1eaa-7c0d-4107-c7e05e391294	{"type": "Child", "ordering": 1565875204348.166102400000132440847495828, "deletedAt": null}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875210117}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875210117}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875168740}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875168753}
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875195590}
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Expanded", "isExpanded": true}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875271886}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875271975}
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe200-95b4-bc0e-4107-c7c9f99d9676	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875295036}
247fe200-95b4-bc0e-4107-c7c9f99d9676	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875295036}
247fe200-95b4-bc0e-4107-c7c9f99d9676	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe18e-0f70-4809-4107-c7edec97f3de	247fe200-95b4-bc0e-4107-c7c9f99d9676	{"type": "Child", "ordering": 1565875026941.166102400000100480317214689, "deletedAt": null}
247fe206-0192-430f-4107-c7a480550d5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875308897}
247fe206-0192-430f-4107-c7a480550d5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875308897}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	247fe206-0192-430f-4107-c7a480550d5b	{"type": "Child", "ordering": 1565875308899.166102400000152183721717083, "deletedAt": null}
247fe206-0192-430f-4107-c7a480550d5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875314561}
247fde34-76b8-1d04-4107-c5f2ad2add9d	247fe208-389c-6310-4107-c61a7cd9f32e	{"type": "Automated"}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875324290}
247fe31d-ac85-8916-4107-c655528dc21e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565876024039}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875325883}
247fe31d-ac85-8916-4107-c655528dc21e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565876024039}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875325897}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875325897}
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe211-5156-6011-4107-c7350dc8125b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875337822}
247fe211-5156-6011-4107-c7350dc8125b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875337822}
247fe208-389c-6310-4107-c61a7cd9f32e	247fe211-5156-6011-4107-c7350dc8125b	{"type": "Child", "ordering": 1565875337824.166102400000171705058505307, "deletedAt": null}
247fe211-5156-6011-4107-c7350dc8125b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe220-dc39-a012-4107-c739796d8a4b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875377597}
247fe220-dc39-a012-4107-c739796d8a4b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875377597}
247fe208-389c-6310-4107-c61a7cd9f32e	247fe220-dc39-a012-4107-c739796d8a4b	{"type": "Child", "ordering": 1565875377568.166102400000181724044380747, "deletedAt": null}
247fe220-dc39-a012-4107-c739796d8a4b	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe208-389c-6310-4107-c61a7cd9f32e	247fe011-7658-9810-4107-c8227f4c6905	{"type": "ReferencesTemplate", "isCreate": false, "isRename": false}
247fe22e-a84f-1113-4107-c6e3d4b4ba33	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875412878}
247fde35-a442-6105-4107-c753300cd7a2	247fe22e-a84f-1113-4107-c6e3d4b4ba33	{"type": "Automated"}
247fde35-a442-6105-4107-c753300cd7a2	247fe22e-a84f-1113-4107-c6e3d4b4ba33	{"type": "Child", "ordering": 1565875412849.166102400000191356208585267, "deletedAt": null}
247fe22e-a84f-1113-4107-c6e3d4b4ba33	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875420657}
247fe22e-a84f-1113-4107-c6e3d4b4ba33	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875420657}
247fe22e-a84f-1113-4107-c6e3d4b4ba33	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe233-c8e3-a814-4107-c80cd04e0903	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875425960}
247fe233-c8e3-a814-4107-c80cd04e0903	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875425960}
247fe22e-a84f-1113-4107-c6e3d4b4ba33	247fe233-c8e3-a814-4107-c80cd04e0903	{"type": "Child", "ordering": 1565875425960.166102400000202631740033283, "deletedAt": null}
247fe233-c8e3-a814-4107-c80cd04e0903	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875709355}
247fe006-fe1f-840f-4107-c731ee179275	247fe011-7658-9810-4107-c8227f4c6905	{"key": "Teamprojekt", "type": "LabeledProperty", "showOnCard": true}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875874506}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565875874532}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565875874532}
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe31d-ac85-8916-4107-c655528dc21e	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fde4c-521b-8a06-4107-c61c216403e3	247fe31d-ac85-8916-4107-c655528dc21e	{"key": "Invite-Email", "type": "LabeledProperty", "showOnCard": false}
247fe3ab-444d-fe00-1531-1aba7f4ff6f2	247fe3ab-444d-fe00-1531-1aba7f4ff6f2	{"type": "Member", "level": "readwrite"}
247fe3c7-6d89-2900-1531-1b012f3f05b9	247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Member", "level": "readwrite"}
247fde2e-90ea-9401-4107-c68110e68c92	247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Member", "level": "readwrite"}
247fde2e-90ea-9401-4107-c68110e68c92	247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Read", "timestamp": 1565876459895}
247fde2e-90ea-9401-4107-c68110e68c92	247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Notify"}
247fde2e-90ea-9401-4107-c68110e68c92	247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Pinned"}
246ded7f-ae61-db00-0beb-c341f9e67fd0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565876869699}
246ded7f-ae61-db00-0beb-c341f9e67fd0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565876869885}
246ded7f-ae61-db00-0beb-c341f9e67fd0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565876869885}
246ded7f-ae61-db00-0beb-c341f9e67fd0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe499-013f-9100-1531-1bf25ba12bee	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Member", "level": "readwrite"}
247fde2e-90ea-9401-4107-c68110e68c92	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Read", "timestamp": 1565877011713}
247fde2e-90ea-9401-4107-c68110e68c92	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Notify"}
247fe03e-e576-ec11-4107-c803db410fc0	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Read", "timestamp": 1565877045831}
247fe011-7658-9810-4107-c8227f4c6905	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Read", "timestamp": 1565877047859}
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Read", "timestamp": 1565877255554}
247fe011-7658-9810-4107-c8227f4c6905	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Notify"}
247fe011-7658-9810-4107-c8227f4c6905	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Member", "level": "readwrite"}
247fe011-7658-9810-4107-c8227f4c6905	247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Invite"}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565964078173}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565964078519}
247fde4c-521b-8a06-4107-c61c216403e3	247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	{"key": "Teilnehmer", "type": "LabeledProperty", "showOnCard": false}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966512365}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966512787}
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966537249}
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966537526}
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565966537526}
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966560688}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966582738}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966616424}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565966984087}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1565966984087}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565967020983}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1565967021293}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145028855}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145781572}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145030991}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145150594}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145150944}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145781890}
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Notify"}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145351819}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145352132}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145465193}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145826506}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145465521}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566145826820}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566229763837}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Author", "timestamp": 1566229764304}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Read", "timestamp": 1566229764304}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "Member", "level": "readwrite"}
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	graph	SQL	V1__graph.sql	422010744	wust	2019-07-11 17:26:18.562854	71	t
2	2	user	SQL	V2__user.sql	410358264	wust	2019-07-11 17:26:18.645854	40	t
3	3	incidence index	SQL	V3__incidence_index.sql	1300472430	wust	2019-07-11 17:26:18.694557	25	t
4	4	user revision	SQL	V4__user_revision.sql	-2009960029	wust	2019-07-11 17:26:18.730933	1	t
5	5	user implicit	SQL	V5__user_implicit.sql	-449324864	wust	2019-07-11 17:26:18.742262	1	t
6	6	user ownership	SQL	V6__user_ownership.sql	-2029942174	wust	2019-07-11 17:26:18.753612	77	t
7	6.1	put old posts into public group	SQL	V6.1__put_old_posts_into_public_group.sql	66940450	wust	2019-07-11 17:26:18.838629	1	t
8	6.2	give groupless users a personal group	SQL	V6.2__give_groupless_users_a_personal_group.sql	-492950262	wust	2019-07-11 17:26:18.846706	1	t
9	7	invite token	SQL	V7__invite_token.sql	884677346	wust	2019-07-11 17:26:18.855571	36	t
10	8	rename usergroupmember usergroupinvite connects contains	SQL	V8__rename_usergroupmember_usergroupinvite_connects_contains.sql	128024823	wust	2019-07-11 17:26:18.902426	2	t
11	9	membership userid set not null	SQL	V9__membership_userid_set_not_null.sql	503128850	wust	2019-07-11 17:26:18.911097	1	t
12	10	eliminate atoms and views	SQL	V10__eliminate_atoms_and_views.sql	-1930295764	wust	2019-07-11 17:26:18.919802	48	t
13	11	forbid self loops	SQL	V11__forbid_self_loops.sql	-1891569201	wust	2019-07-11 17:26:18.976928	4	t
14	12	post uuid	SQL	V12__post_uuid.sql	-559146598	wust	2019-07-11 17:26:18.987075	70	t
15	13	rawpost	SQL	V13__rawpost.sql	-897484842	wust	2019-07-11 17:26:19.065356	10	t
16	14	connection label	SQL	V14__connection_label.sql	679233733	wust	2019-07-11 17:26:19.082283	53	t
17	15	connection view	SQL	V15__connection_view.sql	1511843836	wust	2019-07-11 17:26:19.142408	4	t
18	16	posts author timestamp	SQL	V16__posts_author_timestamp.sql	2075479455	wust	2019-07-11 17:26:19.153492	31	t
19	17	user uuid	SQL	V17__user_uuid.sql	1922426677	wust	2019-07-11 17:26:19.191707	107	t
20	18	flatten ownership	SQL	V18__flatten_ownership.sql	753207422	wust	2019-07-11 17:26:19.308392	6	t
21	19	web push subscription	SQL	V19__web_push_subscription.sql	504150572	wust	2019-07-11 17:26:19.323376	22	t
22	20	graph page stored procedure	SQL	V20__graph_page_stored_procedure.sql	-909046012	wust	2019-07-11 17:26:19.354065	7	t
23	21	membership primary key	SQL	V21__membership_primary_key.sql	-1027673461	wust	2019-07-11 17:26:19.368516	10	t
24	22	notified users	SQL	V22__notified_users.sql	-1521588381	wust	2019-07-11 17:26:19.386749	3	t
25	23	traverse children 10000 distinct	SQL	V23__traverse_children_10000_distinct.sql	1198124118	wust	2019-07-11 17:26:19.397712	2	t
26	24	merge users	SQL	V24__merge_users.sql	-2091283145	wust	2019-07-11 17:26:19.406797	1	t
27	25	merge users	SQL	V25__merge_users.sql	-1947983923	wust	2019-07-11 17:26:19.414401	1	t
28	26	locked posts	SQL	V26__locked_posts.sql	-1018406840	wust	2019-07-11 17:26:19.422509	2	t
29	27	stop traversal	SQL	V27__stop_traversal.sql	-464539729	wust	2019-07-11 17:26:19.4329	7	t
30	28	post join level	SQL	V28__post_join_level.sql	-1928183518	wust	2019-07-11 17:26:19.447296	7	t
31	29	post type	SQL	V29__post_type.sql	-226344795	wust	2019-07-11 17:26:19.461545	3	t
32	30	post type json	SQL	V30__post_type_json.sql	-443913409	wust	2019-07-11 17:26:19.472502	34	t
33	31	joindate level indices	SQL	V31__joindate_level_indices.sql	-1053681814	wust	2019-07-11 17:26:19.515701	36	t
34	32	user channelpost	SQL	V32__user_channelpost.sql	-1366458843	wust	2019-07-11 17:26:19.559059	4	t
35	33	get graph with orphans	SQL	V33__get_graph_with_orphans.sql	-260221320	wust	2019-07-11 17:26:19.572748	1	t
36	34	get graph with orphans fix	SQL	V34__get_graph_with_orphans_fix.sql	1138914950	wust	2019-07-11 17:26:19.583066	1	t
37	35	post content json format and index	SQL	V35__post_content_json_format_and_index.sql	1748444894	wust	2019-07-11 17:26:19.590484	10	t
38	36	connection content json	SQL	V36__connection_content_json.sql	47379368	wust	2019-07-11 17:26:19.606964	29	t
39	37	userpost	SQL	V37__userpost.sql	183314684	wust	2019-07-11 17:26:19.642924	2	t
40	38	drop rawpost deleted date	SQL	V38__drop_rawpost_deleted_date.sql	-1628622122	wust	2019-07-11 17:26:19.651892	55	t
41	39	membership in graph	SQL	V39__membership_in_graph.sql	-1315520653	wust	2019-07-11 17:26:19.715019	29	t
42	40	fix node types	SQL	V40__fix_node_types.sql	-909748718	wust	2019-07-11 17:26:19.750702	0	t
43	41	fix graph page authors	SQL	V41__fix_graph_page_authors.sql	245802768	wust	2019-07-11 17:26:19.7552	7	t
44	42	index on user data	SQL	V42__index_on_user_data.sql	1578997633	wust	2019-07-11 17:26:19.76903	18	t
45	43	uuid datatype for cuid	SQL	V43__uuid_datatype_for_cuid.sql	-21881601	wust	2019-07-11 17:26:19.794025	146	t
46	44	uuid datatype for channelnodeid	SQL	V44__uuid_datatype_for_channelnodeid.sql	674178344	wust	2019-07-11 17:26:19.94912	3	t
47	45	fix graph page with orphans	SQL	V45__fix_graph_page_with_orphans.sql	-1298627536	wust	2019-07-11 17:26:19.958656	9	t
48	46	fix merge users	SQL	V46__fix_merge_users.sql	1762174998	wust	2019-07-11 17:26:19.976307	12	t
49	47	permissions	SQL	V47__permissions.sql	-1179191409	wust	2019-07-11 17:26:19.996045	15	t
50	48	fix memberships	SQL	V48__fix_memberships.sql	-1570752424	wust	2019-07-11 17:26:20.018779	0	t
51	49	transitive permission	SQL	V49__transitive_permission.sql	-2008491613	wust	2019-07-11 17:26:20.026199	11	t
52	50	remove redundant node index	SQL	V50__remove_redundant_node_index.sql	-743970324	wust	2019-07-11 17:26:20.044957	1	t
53	51	fix wrong Deleted parent timestamp	SQL	V51__fix_wrong_Deleted_parent_timestamp.sql	1596984275	wust	2019-07-11 17:26:20.053429	1	t
54	52	really fix Deleted parent timestamp	SQL	V52__really_fix_Deleted_parent_timestamp.sql	-720205497	wust	2019-07-11 17:26:20.061506	1	t
55	53	feedback node	SQL	V53__feedback_node.sql	1669460502	wust	2019-07-11 17:26:20.07086	1	t
56	54	merge channelnode of user	SQL	V54__merge_channelnode_of_user.sql	-177139990	wust	2019-07-11 17:26:20.078844	11	t
57	55	notified users update	SQL	V55__notified_users_update.sql	-1742243042	wust	2019-07-11 17:26:20.097516	11	t
58	56	check access on channels	SQL	V56__check_access_on_channels.sql	-1589527006	wust	2019-07-11 17:26:20.116172	10	t
59	57	fix can access node in get page	SQL	V57__fix_can_access_node_in_get_page.sql	812016133	wust	2019-07-11 17:26:20.133227	8	t
60	58	unique index subscription data	SQL	V58__unique_index_subscription_data.sql	69069576	wust	2019-07-11 17:26:20.148635	10	t
61	59	deleted parent to parent	SQL	V59__deleted_parent_to_parent.sql	1723515762	wust	2019-07-11 17:26:20.165165	1	t
62	60	complete channels in page	SQL	V60__complete_channels_in_page.sql	-1787067741	wust	2019-07-11 17:26:20.172779	10	t
63	61	no channelnode	SQL	V61__no_channelnode.sql	-2072901625	wust	2019-07-11 17:26:20.189937	10	t
64	62	remove link	SQL	V62__remove_link.sql	-777652340	wust	2019-07-11 17:26:20.20682	0	t
65	63	partial index edges	SQL	V63__partial_index_edges.sql	1654930647	wust	2019-07-11 17:26:20.214176	18	t
66	64	push travers up	SQL	V64__push_travers_up.sql	-672234660	wust	2019-07-11 17:26:20.239348	1	t
67	65	update procedures	SQL	V65__update_procedures.sql	-1080621706	wust	2019-07-11 17:26:20.247201	9	t
68	66	node role	SQL	V66__node_role.sql	-1287202733	wust	2019-07-11 17:26:20.26287	9	t
69	67	clean before	SQL	V67__clean_before.sql	882548856	wust	2019-07-11 17:26:20.278941	9	t
70	68	transitive children of staticparentin to task	SQL	V68__transitive_children_of_staticparentin_to_task.sql	1002183035	wust	2019-07-11 17:26:20.295185	12	t
71	69	convert staticparentin to expand	SQL	V69__convert_staticparentin_to_expand.sql	677661336	wust	2019-07-11 17:26:20.314127	0	t
72	70	unique before index	SQL	V70__unique_before_index.sql	1607020048	wust	2019-07-11 17:26:20.320604	9	t
73	71	type edge index	SQL	V71__type_edge_index.sql	1431330248	wust	2019-07-11 17:26:20.338263	9	t
74	72	notified users by nodes	SQL	V72__notified_users_by_nodes.sql	-1058897968	wust	2019-07-11 17:26:20.353329	11	t
75	73	subscriptions by nodeid	SQL	V73__subscriptions_by_nodeid.sql	-1603371033	wust	2019-07-11 17:26:20.370531	9	t
76	74	fix get graph own user	SQL	V74__fix_get_graph_own_user.sql	-1468249335	wust	2019-07-11 17:26:20.386394	10	t
77	75	graph page cte	SQL	V75__graph_page_cte.sql	1374112407	wust	2019-07-11 17:26:20.403015	30	t
78	76	notified channels	SQL	V76__notified_channels.sql	-341050881	wust	2019-07-11 17:26:20.439536	9	t
79	77	notified channels respect deleted	SQL	V77__notified_channels_respect_deleted.sql	-210715231	wust	2019-07-11 17:26:20.455456	9	t
80	78	timestamp to millis	SQL	V78__timestamp_to_millis.sql	232132141	wust	2019-07-11 17:26:20.470954	9	t
81	79	user details table	SQL	V79__user_details_table.sql	1553727380	wust	2019-07-11 17:26:20.48609	28	t
82	80	graph page children of user	SQL	V80__graph_page_children_of_user.sql	845513919	wust	2019-07-11 17:26:20.52082	7	t
83	81	partial index on file key	SQL	V81__partial_index_on_file_key.sql	2100838062	wust	2019-07-11 17:26:20.534406	9	t
84	82	assigned in subgraph	SQL	V82__assigned_in_subgraph.sql	979039810	wust	2019-07-11 17:26:20.550233	8	t
85	83	heuristic for stage role	SQL	V83__heuristic_for_stage_role.sql	1231825842	wust	2019-07-11 17:26:20.564587	9	t
86	84	remove before edges	SQL	V84__remove_before_edges.sql	-933870302	wust	2019-07-11 17:26:20.579672	18	t
87	85	new stage encoding heuristic	SQL	V85__new_stage_encoding_heuristic.sql	-1817321646	wust	2019-07-11 17:26:20.605105	9	t
88	86	graph page returns orphans	SQL	V86__graph_page_returns_orphans.sql	-190913261	wust	2019-07-11 17:26:20.621053	8	t
89	87	graph page not returns orphans	SQL	V87__graph_page_not_returns_orphans.sql	1470812692	wust	2019-07-11 17:26:20.635896	8	t
90	88	login via email	SQL	V88__login_via_email.sql	-64283707	wust	2019-07-11 17:26:20.650324	0	t
91	89	get graph with properties	SQL	V89__get_graph_with_properties.sql	472175117	wust	2019-07-11 17:26:20.657007	7	t
92	90	delete labeled edges	SQL	V90__delete_labeled_edges.sql	-1574834705	wust	2019-07-11 17:26:20.670516	7	t
93	91	undelete autodeleted nodes	SQL	V91__undelete_autodeleted_nodes.sql	-1871913341	wust	2019-07-11 17:26:20.684097	9	t
94	92	delete property edges	SQL	V92__delete_property_edges.sql	-1188890913	wust	2019-07-11 17:26:20.699052	7	t
95	93	delete property nodes	SQL	V93__delete_property_nodes.sql	121453438	wust	2019-07-11 17:26:20.713052	11	t
96	94	return automated and implements in graph page	SQL	V94__return_automated_and_implements_in_graph_page.sql	-558205192	wust	2019-07-11 17:26:20.730827	10	t
97	95	node with views	SQL	V95__node_with_views.sql	-1679668308	wust	2019-07-11 17:26:20.747392	10	t
98	96	edge direction by convention	SQL	V96__edge_direction_by_convention.sql	946635630	wust	2019-07-11 17:26:20.765346	65	t
99	97	induce users	SQL	V97__induce_users.sql	2105164653	wust	2019-07-11 17:26:20.83919	12	t
100	98	attach files	SQL	V98__attach_files.sql	1713433130	wust	2019-07-11 17:26:20.858917	3	t
101	99	unique edges with properties	SQL	V99__unique_edges_with_properties.sql	284580357	wust	2019-07-11 17:26:20.869352	10	t
102	100	expanded data	SQL	V100__expanded_data.sql	-102388515	wust	2019-07-11 17:26:20.887839	1	t
103	101	push notifications on deepest node	SQL	V101__push_notifications_on_deepest_node.sql	-335380412	wust	2019-07-11 17:26:20.896314	15	t
104	102	mandatory ordering number	SQL	V102__mandatory_ordering_number.sql	1245012471	wust	2019-07-11 17:26:20.919312	4	t
105	103	public only as member	SQL	V103__public_only_as_member.sql	1604478736	wust	2019-07-11 17:26:20.930257	11	t
106	104	add oauth client table	SQL	V104__add_oauth_client_table.sql	1381527618	wust	2019-07-11 17:26:20.947937	42	t
107	105	can access node cache	SQL	V105__can_access_node_cache.sql	-1764713453	wust	2019-07-11 17:26:20.99777	12	t
108	106	can access node via url	SQL	V106__can_access_node_via_url.sql	-1174828598	wust	2019-07-11 17:26:21.017238	15	t
109	107	access and automation	SQL	V107__access_and_automation.sql	1561316891	wust	2019-07-11 17:26:21.039413	11	t
110	108	can access caching	SQL	V108__can_access_caching.sql	-1955591207	wust	2019-07-11 17:26:21.057461	10	t
111	109	labeled property hidden on card	SQL	V109__labeled_property_hidden_on_card.sql	-1199537927	wust	2019-07-11 17:26:21.074914	1	t
112	110	references template options	SQL	V110__references_template_options.sql	-1276932318	wust	2019-07-12 12:47:23.445511	7	t
113	111	propagate access through automated edges	SQL	V111__propagate_access_through_automated_edges.sql	1912832436	wust	2019-08-15 11:32:05.696429	8	t
114	112	used features	SQL	V112__used_features.sql	-1828866938	wust	2019-08-15 11:32:05.715707	21	t
115	113	truncate used features	SQL	V113__truncate_used_features.sql	157643428	wust	2019-08-15 11:32:05.746497	19	t
116	114	node access materialized	SQL	V114__node_access_materialized.sql	-918085889	wust	2019-08-15 11:32:05.773103	50	t
117	115	node access recursive fix	SQL	V115__node_access_recursive_fix.sql	-1897107126	wust	2019-08-15 11:32:05.830887	13	t
118	116	bookmarks with sub projects	SQL	V116__bookmarks_with_sub_projects.sql	-1780069374	wust	2019-08-16 13:58:58.165407	25	t
119	117	fix missing deleted checks in traversal	SQL	V117__fix_missing_deleted_checks_in_traversal.sql	137972140	wust	2019-08-16 13:58:58.200578	14	t
120	118	fix non trigger on node update accesslevel null	SQL	V118__fix_non_trigger_on_node_update_accesslevel_null.sql	-1965355466	wust	2019-08-16 13:58:58.224072	13	t
\.


--
-- Data for Name: node; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.node (id, data, accesslevel, role, views) FROM stdin;
00a15824-84ca-ebde-0349-f7f0e8b98ead	{"type": "PlainText", "content": "Woost Feedback"}	readwrite	{"type": "Message"}	\N
246dedc6-243a-0a00-0beb-c4d86329b3b4	{"name": "klaus", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedc9-7a4a-3d00-0beb-c52921a63779	{"name": "laura", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedca-929b-2d00-0beb-c3cb96965ce3	{"name": "michael", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedcb-974c-d900-0beb-c4e3a7d0dff6	{"name": "marie", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedcc-c055-d000-0beb-c4dbf20d81d0	{"name": "petra", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	{"name": "harald", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedcf-0d99-8e00-0beb-c4352b0feb3a	{"name": "bernd", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedd0-2236-3900-0beb-c3131fbba3b8	{"name": "eva", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedd1-194a-5d00-0beb-c5195068e4cc	{"name": "felix", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246dedd2-3468-e900-0beb-c4797c30fedf	{"name": "sarah", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246def1f-16b9-bf02-0beb-c3f1824d4e88	{"type": "Markdown", "content": ":computer: IT"}	\N	{"type": "Project"}	\N
246def70-4f60-8b03-0beb-c4e0e34528ce	{"type": "Markdown", "content": ":heart: Conversion"}	\N	{"type": "Project"}	\N
246df0ec-3945-a50e-0beb-c4518b8ef3b9	{"type": "Markdown", "content": "Deployed"}	\N	{"type": "Stage"}	\N
246df09c-af4e-6705-0beb-c37d90e281e1	{"type": "DateTime", "content": 1561975200000}	\N	{"type": "Neutral"}	\N
246df0c5-a8ef-f108-0beb-c31717c4359a	{"type": "DateTime", "content": 1564653600000}	\N	{"type": "Neutral"}	\N
246def6a-9639-8001-0beb-c2b7d2e661e4	{"type": "Markdown", "content": ":rocket: Growth Hacking"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Kanban\\"}"}
246df0e9-03d8-780c-0beb-c51f81463c9d	{"type": "Markdown", "content": "In Progress"}	\N	{"type": "Stage"}	\N
246df0e9-ed9b-f70d-0beb-c3962616de23	{"type": "Markdown", "content": "Testing"}	\N	{"type": "Stage"}	\N
246e592c-6ced-e702-3f11-23a4149259fc	{"type": "Markdown", "content": "Christian Polster"}	\N	{"type": "Task"}	\N
246def3f-d27a-160a-0beb-c4767631b609	{"type": "Markdown", "content": ":busts_in_silhouette: Onboarding"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Kanban\\"}","{\\"type\\": \\"Table\\", \\"roles\\": [{\\"type\\": \\"Task\\"}]}"}
246e593e-835a-1303-3f11-2403b117bcf7	{"type": "Markdown", "content": "Peter Grund"}	\N	{"type": "Task"}	\N
246e5960-e42d-9806-3f11-251c314abb60	{"type": "Markdown", "content": "Live | Deployed"}	\N	{"type": "Stage"}	\N
246e5d05-bb05-d709-3f11-23bf6f593df9	{"type": "Markdown", "content": "Explain collaboration software"}	\N	{"type": "Task"}	\N
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	{"type": "Markdown", "content": "Introduce to team"}	\N	{"type": "Task"}	\N
246e592a-56d3-b001-3f11-256967ea9c5b	{"type": "Markdown", "content": "Manuela"}	\N	{"type": "Task"}	\N
246dedbb-a4e5-4f01-0beb-c446b4802a70	{"type": "Markdown", "content": ":office: Aix AG"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Content\\"}","{\\"type\\": \\"Kanban\\"}"}
246e59f1-cc84-a905-3f11-255b12ce45d4	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246def1b-8fe8-4501-0beb-c468922ff921	{"type": "Markdown", "content": ":man-woman-boy: Personalwesen"}	\N	{"type": "Project"}	\N
246def39-9145-5c08-0beb-c2d1bfcb6705	{"type": "Markdown", "content": ":mortar_board: Fortbildung"}	\N	{"type": "Project"}	\N
246def3c-ca66-ce09-0beb-c4189521dfec	{"type": "Markdown", "content": ":ear: Beschwerden"}	\N	{"type": "Project"}	\N
246def43-bdb5-5b0b-0beb-c3568a4a0a45	{"type": "Markdown", "content": ":incoming_envelope: Personalbeschaffung"}	\N	{"type": "Project"}	\N
246def6d-bc08-9102-0beb-c3b2aeeee6b1	{"type": "Markdown", "content": ":woman-raising-hand: Bekanntheit"}	\N	{"type": "Project"}	\N
246def73-32cf-a504-0beb-c3a4ce0f0647	{"type": "Markdown", "content": ":anchor: Kundenbindung"}	\N	{"type": "Project"}	\N
246def24-b940-a804-0beb-c31ab1abee87	{"type": "Markdown", "content": ":wrench: Entwicklung"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Kanban\\"}"}
246def27-4148-9105-0beb-c4170f451769	{"type": "Markdown", "content": ":heavy_dollar_sign: Vertrieb"}	\N	{"type": "Project"}	\N
246e580e-7a90-e902-3f11-245128873461	{"type": "Markdown", "content": ":truck: Eingangslogistik"}	\N	{"type": "Project"}	\N
246e580f-f932-9703-3f11-24ec645ceced	{"type": "Markdown", "content": ":airplane: Ausgangslogistik"}	\N	{"type": "Project"}	\N
246df063-4821-c501-0beb-c30c29953475	{"type": "Markdown", "content": "# Informationen über die Aix AG\\n\\n### Geschichte\\n- Gegründet 1965\\n\\n### CEO: Manager"}	\N	{"type": "Note"}	\N
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	{"type": "Markdown", "content": "Vorbereitung"}	\N	{"type": "Stage"}	\N
246df0d4-468e-af0a-0beb-c32aaa58952c	{"type": "Markdown", "content": "Erster Tag"}	\N	{"type": "Stage"}	\N
246df0d6-17fb-780b-0beb-c4873e10525b	{"type": "Markdown", "content": "Nachbereitung"}	\N	{"type": "Stage"}	\N
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	{"type": "Markdown", "content": "Studentenjobs"}	\N	{"type": "Task"}	\N
246df08d-6316-d903-0beb-c32b118ae54f	{"type": "Markdown", "content": "Urlaub"}	\N	{"type": "Task"}	\N
246df0f8-b5e0-9f0f-0beb-c361d12451f9	{"type": "Markdown", "content": "Vorbereitung"}	\N	{"type": "Stage"}	\N
246df0fa-0226-f610-0beb-c3a35b86f228	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246df0fd-9ab0-c112-0beb-c2daba94f8cd	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
246df0fd-2274-7011-0beb-c5026310961d	{"type": "Markdown", "content": "Rollout"}	\N	{"type": "Stage"}	\N
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246e595f-38e5-6c05-3f11-24ce4fa8e56e	{"type": "Markdown", "content": "Testphase"}	\N	{"type": "Stage"}	\N
246e59da-2c18-a901-3f11-255316ece461	{"type": "Markdown", "content": "Template in: In Bearbeitung"}	\N	{"type": "Task"}	{"{\\"type\\": \\"Kanban\\"}"}
246e59e1-851e-2e02-3f11-23cc61231036	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246e59e2-824d-af03-3f11-23a005bd4630	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
246e59fc-6246-4406-3f11-259301538327	{"type": "Markdown", "content": "Release Notes"}	\N	{"type": "Task"}	\N
246e5a18-a522-6f09-3f11-250bca87bdec	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	{"type": "Markdown", "content": "Firefox"}	\N	{"type": "Task"}	\N
246e5a2d-7070-440d-3f11-2391a98a7349	{"type": "Markdown", "content": "Chrome"}	\N	{"type": "Task"}	\N
246e5a2e-47c8-0b0e-3f11-246cc4033b70	{"type": "Markdown", "content": "Safari"}	\N	{"type": "Task"}	\N
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	{"type": "Markdown", "content": "Template in: Live | Deployed"}	\N	{"type": "Task"}	\N
246e5a40-02cd-0611-3f11-243db530640f	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5d31-a500-9c0d-3f11-25b18d58d025	{"type": "Markdown", "content": "Template in: Follow-Up"}	\N	{"type": "Task"}	\N
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	{"type": "Placeholder", "targetType": {"data": "Decimal", "type": "Data"}}	\N	{"type": "Neutral"}	\N
246e5ae8-2b0d-d31f-3f11-260751da15f1	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5afd-bb2e-1622-3f11-25cafbc898ed	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5b33-19ca-2e27-3f11-25db2be6f6fd	{"type": "RelativeDate", "content": 0}	\N	{"type": "Neutral"}	\N
246e5b6f-0e80-502a-3f11-249340305022	{"type": "Markdown", "content": "Template in: Preparations"}	\N	{"type": "Task"}	\N
246e5b7a-005c-832b-3f11-23f35d51bc74	{"type": "Markdown", "content": "Template in: Preparations"}	\N	{"type": "Task"}	\N
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	{"type": "Markdown", "content": "Template in: Preparations"}	\N	{"type": "Task"}	\N
246e5b91-c051-9e02-3f11-26196575e75b	{"type": "Placeholder", "targetType": {"type": "Ref"}}	\N	{"type": "Neutral"}	\N
246e5bae-0514-bc04-3f11-257ec75b65c1	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
246e5cbd-06ba-fe05-3f11-24643fd81dd2	{"type": "Markdown", "content": "Setup workstation"}	\N	{"type": "Task"}	\N
246e5ccb-5cab-7006-3f11-2608c9423927	{"type": "Markdown", "content": "Inform team"}	\N	{"type": "Task"}	\N
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	{"type": "Markdown", "content": "Assign advisor"}	\N	{"type": "Task"}	\N
246e5d3a-b785-9b0e-3f11-2424adf15a20	{"type": "Markdown", "content": "Explain policies and security guidelines"}	\N	{"type": "Task"}	\N
246e5d62-32b8-b90f-3f11-24715d4a6f48	{"type": "Markdown", "content": "Doing"}	\N	{"type": "Stage"}	\N
246e5d93-e077-3312-3f11-23efb628d24f	{"type": "Markdown", "content": "Create E-Mail"}	\N	{"type": "Task"}	\N
246e5d62-cfe6-1b10-3f11-24a4ed39a889	{"type": "Markdown", "content": "Done"}	\N	{"type": "Stage"}	\N
246e5d65-a21d-0911-3f11-248b41f6cd32	{"type": "Markdown", "content": "Show around"}	\N	{"type": "Task"}	\N
246e5dbb-fdbf-8414-3f11-26088435221f	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
246e5cec-6a03-5607-3f11-2400c923c1b0	{"type": "Markdown", "content": "Template in: First Day"}	\N	{"type": "Task"}	{"{\\"type\\": \\"Kanban\\"}","{\\"type\\": \\"Chat\\"}"}
246e608f-5b4f-6302-3f11-24cf9a89be03	{"type": "Markdown", "content": "Invite ${woost.parent.field.E-Mail} to board"}	\N	{"type": "Task"}	\N
246e6077-a142-6301-3f11-2399ab4d19b5	{"type": "Markdown", "content": "Hello ${woost.parent} :wave:,\\n\\nwe are happy that you joined our team ${woost.parent.field.Team}.\\n\\nTo make it as easy as possible for you to get started, we created a kanban board for you :smiley: "}	\N	{"type": "Message"}	\N
246e6122-ea9d-b204-3f11-2554ab0b95d3	{"type": "Markdown", "content": "Inform team"}	\N	{"type": "Task"}	\N
246e6122-eab7-5305-3f11-24df71c6e1c2	{"type": "Markdown", "content": "Setup workstation"}	\N	{"type": "Task"}	\N
246e6122-eab7-5306-3f11-254480b90c6e	{"type": "Markdown", "content": "Create E-Mail"}	\N	{"type": "Task"}	\N
246e6122-ea84-1101-3f11-25d4add70289	{"type": "Markdown", "content": "alvarez@localhost"}	\N	{"type": "Neutral"}	\N
246e6122-ea84-1102-3f11-252e6f15e681	{"type": "Markdown", "content": "Manuela Alvarez"}	\N	{"type": "Neutral"}	\N
246e6137-64a0-db07-3f11-26062f9907e8	{"type": "Markdown", "content": "Done"}	\N	{"type": "Stage"}	\N
246e618a-9bdb-130e-3f11-240e34db8b23	{"type": "Markdown", "content": "Done"}	\N	{"type": "Stage"}	\N
246e617e-84c3-cf02-3f11-243871f5d777	{"type": "Markdown", "content": "Release Notes"}	\N	{"type": "Task"}	\N
246e617e-84c3-cf03-3f11-259494710bd2	{"type": "DateTime", "content": 1562942261094}	\N	{"type": "Neutral"}	\N
246e617f-5fcf-db06-3f11-25ea7e718c4d	{"type": "Markdown", "content": "Browser tests"}	\N	{"type": "Task"}	\N
246e5ac3-0955-2d17-3f11-2584d953ac42	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246e5ac3-7b29-3e18-3f11-23d89a57d139	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
246e5b23-5d72-4025-3f11-2477952948a6	{"type": "Markdown", "content": "Template in: Rollout"}	\N	{"type": "Task"}	\N
246e5b44-be95-8b29-3f11-24e0646555ce	{"type": "Markdown", "content": "Messe Performance"}	\N	{"type": "Task"}	\N
246e5af8-7876-0a20-3f11-2513978b8f75	{"type": "Markdown", "content": "Template in: In Bearbeitung"}	\N	{"type": "Task"}	\N
246e5b40-b0b6-ac28-3f11-24bbc9148469	{"type": "Markdown", "content": "Erstelle Bericht"}	\N	{"type": "Task"}	\N
246e5b1e-e0f3-7024-3f11-24efbdc303f4	{"type": "Markdown", "content": "Tracking einrichten"}	\N	{"type": "Task"}	\N
246e5b08-68f6-a123-3f11-25f5adf1dced	{"type": "Markdown", "content": "Werbeanzeigen erstellen"}	\N	{"type": "Task"}	\N
246e5ac6-32f2-2419-3f11-2400a6083c68	{"type": "Markdown", "content": "Definiere Ziele"}	\N	{"type": "Task"}	\N
246e5ac8-3339-411a-3f11-24d6eff5be62	{"type": "Markdown", "content": "Erstelle Tracking Codes"}	\N	{"type": "Task"}	\N
246e5adb-b1da-3b1d-3f11-261dea95c4f2	{"type": "Markdown", "content": "Lege Budget fest"}	\N	{"type": "Task"}	\N
246e5a10-92b4-2e07-3f11-241b79852233	{"type": "Markdown", "content": "Template in: Testphase"}	\N	{"type": "Task"}	\N
246e5a1c-f2c6-eb0a-3f11-23979f076ced	{"type": "Markdown", "content": "Sicherheitstests"}	\N	{"type": "Task"}	\N
246e5a2a-6f45-610b-3f11-249d1a2a7190	{"type": "Markdown", "content": "Browsertests"}	\N	{"type": "Task"}	\N
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	{"type": "Markdown", "content": "Verifiziere Live Status"}	\N	{"type": "Task"}	\N
246e617e-84c3-cf05-3f11-256a564f317c	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246e617e-84c3-cf04-3f11-24ca856b8dc8	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
246e6172-349c-1101-3f11-23b5e165928e	{"type": "Markdown", "content": "Neues Einladungssystem"}	\N	{"type": "Task"}	\N
246e617f-5fcf-db07-3f11-25529d531bf5	{"type": "Markdown", "content": "Firefox"}	\N	{"type": "Task"}	\N
246e617f-5fcf-db08-3f11-23e68e60bf48	{"type": "Markdown", "content": "Safari"}	\N	{"type": "Task"}	\N
246e617f-5fcf-db09-3f11-2502e35db6f2	{"type": "Markdown", "content": "Chrome"}	\N	{"type": "Task"}	\N
246e617f-5fcf-db0a-3f11-23ad06880e1f	{"type": "Markdown", "content": "Security tests"}	\N	{"type": "Task"}	\N
246e617f-5fcf-db0b-3f11-2408b71a2684	{"type": "DateTime", "content": 1562942263286}	\N	{"type": "Neutral"}	\N
246e617f-f82f-0d0c-3f11-25234559f1f6	{"type": "Markdown", "content": "Verify deployment status"}	\N	{"type": "Task"}	\N
246e617f-f82f-0d0d-3f11-23d0fad077c8	{"type": "DateTime", "content": 1562942264808}	\N	{"type": "Neutral"}	\N
246e61b4-c253-7a1a-3f11-251679c00cf7	{"type": "DateTime", "content": 1562942399796}	\N	{"type": "Neutral"}	\N
246e61a5-5044-3210-3f11-247569b3c4da	{"type": "DateTime", "content": 1562942360296}	\N	{"type": "Neutral"}	\N
246e61a5-502a-910f-3f11-2423d7ac2bd1	{"type": "Decimal", "content": 500.0}	\N	{"type": "Neutral"}	\N
246e61b1-ca5e-7318-3f11-23d6d1b63629	{"type": "DateTime", "content": 1562942392206}	\N	{"type": "Neutral"}	\N
246e6375-2ab2-9f01-3f11-24413ca87efd	{"type": "Markdown", "content": ":file_cabinet: Backlog"}	\N	{"type": "Project"}	\N
246def7d-10f5-d605-0beb-c3cc4fdeea75	{"type": "Markdown", "content": ":checkered_flag: Sprints"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Kanban\\"}","{\\"type\\": \\"Table\\", \\"roles\\": [{\\"type\\": \\"Task\\"}]}"}
246f7091-3c0c-e801-3f11-496d471ea961	{"type": "Markdown", "content": "Frontend"}	\N	{"type": "Tag"}	\N
246f7092-6b17-9b02-3f11-47fed8991d27	{"type": "Markdown", "content": "Backend"}	\N	{"type": "Tag"}	\N
246f7093-9d89-b003-3f11-4836856dae24	{"type": "Markdown", "content": "UI"}	\N	{"type": "Tag"}	\N
246f7094-b3f3-ad04-3f11-4922ede189a3	{"type": "Markdown", "content": "UX"}	\N	{"type": "Tag"}	\N
246f70a0-5bea-db05-3f11-49cad99286bd	{"type": "Markdown", "content": "Template in: Frontend"}	\N	{"type": "Task"}	\N
246f70ed-2549-db06-3f11-48b8a016a56b	{"type": "Markdown", "content": "DB"}	\N	{"type": "Tag"}	\N
246f70f1-9029-fb07-3f11-4991f55b98b6	{"type": "Markdown", "content": "Template in: Backend"}	\N	{"type": "Task"}	\N
246f70fd-07f9-a808-3f11-47854803fecc	{"type": "Markdown", "content": "Security"}	\N	{"type": "Tag"}	\N
246f70ff-74f9-c309-3f11-48dc20ca1c68	{"type": "Markdown", "content": "Bug"}	\N	{"type": "Tag"}	\N
246def22-4558-2803-0beb-c3cd98c524d9	{"type": "Markdown", "content": ":loudspeaker: Marketing"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Content\\"}"}
246def2b-5b11-4706-0beb-c4777a1eae11	{"type": "Markdown", "content": ":moneybag: Buchhaltung"}	\N	{"type": "Project"}	\N
246df082-abe5-3002-0beb-c47cf1ef8b08	{"type": "Markdown", "content": ":calendar: Personalplanung"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Table\\", \\"roles\\": [{\\"type\\": \\"Task\\"}]}","{\\"type\\": \\"Kanban\\"}"}
246e5a92-5692-5e13-3f11-2614592c761c	{"type": "Markdown", "content": ":rainbow: Neudesign"}	\N	{"type": "Task"}	\N
246def2f-9ee6-2107-0beb-c2bd2be44a1a	{"type": "Markdown", "content": ":package: Logistik"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Dashboard\\"}","{\\"type\\": \\"Kanban\\"}"}
246e5aa8-28ac-1414-3f11-2406f6f2aac6	{"type": "Markdown", "content": "Referal Programm"}	\N	{"type": "Task"}	\N
246e5aaa-03e8-7f15-3f11-24e5412142ac	{"type": "Markdown", "content": "Sommerschlussverkauf"}	\N	{"type": "Task"}	\N
246e5abe-b762-a616-3f11-259f98f05fd8	{"type": "Markdown", "content": "Template in: Vorbereitung"}	\N	{"type": "Task"}	{"{\\"type\\": \\"Kanban\\"}"}
246e65a2-092f-a803-3f11-25841a2243a6	{"type": "Markdown", "content": "Mehr Platz zwischen Objekten"}	\N	{"type": "Task"}	\N
246e6598-bc99-e502-3f11-23e4066f9d5c	{"type": "Markdown", "content": "Flachen Icon Satz"}	\N	{"type": "Task"}	\N
246e6596-b6d1-3101-3f11-24a80f73c202	{"type": "Markdown", "content": "Neue Farbpalette"}	\N	{"type": "Task"}	\N
246e61b4-c253-7a19-3f11-24a0f85e1a3d	{"type": "Markdown", "content": "Erstelle Bericht"}	\N	{"type": "Task"}	\N
246e61a5-505d-d315-3f11-2427f82d1a58	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
246e61a5-505d-d314-3f11-25160bc7db52	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
246e61b4-c253-7a1b-3f11-24550f613706	{"type": "Markdown", "content": "Messe Performance"}	\N	{"type": "Task"}	\N
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	{"type": "Markdown", "content": "Erstelle Werbeanzeige"}	\N	{"type": "Task"}	\N
246e61a5-5044-3213-3f11-26087a823003	{"type": "Markdown", "content": "Definiere Ziele"}	\N	{"type": "Task"}	\N
246e61a5-5044-3212-3f11-25a04fd48f11	{"type": "Markdown", "content": "Erstelle Tracking Codes"}	\N	{"type": "Task"}	\N
246e61a5-5044-3211-3f11-2521cff85b61	{"type": "Markdown", "content": "Lege Budget fest"}	\N	{"type": "Task"}	\N
246e61b1-ca5e-7316-3f11-240f0836267b	{"type": "Markdown", "content": "Tracking einrichten"}	\N	{"type": "Task"}	\N
246fed72-54b1-cc01-3f11-480f760fe40e	{"type": "Markdown", "content": "# Marketing\\nDer Begriff **Marketing** oder (deutsch) Absatzwirtschaft bezeichnet zum einen den Unternehmensbereich, dessen Aufgabe (Funktion) es ist, Produkte und Dienstleistungen zu vermarkten (zum Verkauf anbieten in einer Weise, dass Käufer dieses Angebot als wünschenswert wahrnehmen); zum anderen beschreibt dieser Begriff ein Konzept der ganzheitlichen, marktorientierten Unternehmensführung zur Befriedigung der Bedürfnisse und Erwartungen von Kunden und anderen Interessengruppen (Stakeholder). Damit entwickelt sich das Marketingverständnis von einer operativen Technik zur Beeinflussung der Kaufentscheidung (Marketing-Mix-Instrumente) hin zu einer Führungskonzeption, die andere Funktionen wie zum Beispiel Beschaffung, Produktion, Verwaltung und Personal mit einschließt.\\n\\n## Marketing als Unternehmensfunktion\\nIn der Betriebswirtschaftslehre ist das Marketing ein Teil des unternehmerischen Gesamtprozesses. Dies beginnt mit der Planung eines Konzeptes, worauf der Einkauf von Rohstoffen und Vorprodukten (Vorleistungen) folgt, führt weiter zur Produktion (Erstellung von Gütern oder Dienstleistungen) und endet mit der Vermarktung (Marketing bzw. Vertrieb) der erstellten betrieblichen Leistungen. Hinzu kommen unterstützende Prozesse wie zum Beispiel Innovation, Finanzierung, Verwaltung oder Personalwirtschaft. Die Teilprozesse werden auch als betriebliche oder unternehmerische Funktionen bezeichnet. Damit alle Prozesse möglichst reibungslos funktionieren, bedarf es der Managementfunktionen. Dazu gehören Planung (einschließlich Zielsetzung), Organisation, Führung und Kontrolle (Erfolgs- und Fortschrittskontrolle) im Hinblick auf die Zielsetzung. Den Marketingprozess selbst kann man als Marketingplan darstellen:\\n1. Erkennen von Chancen durch die Markt-, Kunden- und Wettbewerbsanalyse einschließlich Marktforschung, \\n2. Festlegung von Zielen, die sicherstellen, dass die investierten Mittel zurückfließen,\\n3. Auswahl geeigneter Strategien zur Zielerreichung,\\n4. Umsetzung der Strategie mit dem Marketing-Mix und schließlich\\n5. Erfolgskontrolle des gesamten Prozesses und aller getroffenen Entscheidungen.\\n\\n\\nDas Thema Marketing bekam in der Wissenschaft und in der Praxis eine große Bedeutung mit dem Wandel von der kriegsbedingten Mangelwirtschaft (Nachfrage ist größer als das Angebot) hin zur sogenannten Überflussgesellschaft (Angebot ist größer als die Nachfrage) seit Mitte der 1950er Jahre. Dieser Trend war von einem verstärkten Wettbewerb um Kunden begleitet und wurde durch die beiden Ölkrisen der 1970er Jahre verstärkt. Beispielsweise erfolgte im Jahr 1969 die Gründung des ersten Marketinglehrstuhls (Heribert Meffert) in Deutschland. Seither gilt das Marketing als zentraler Erfolgsfaktor für die langfristige „Überlebensfähigkeit“ von Unternehmen im Wettbewerb (Dominanz der Marketingfunktion). Die nebenstehende Grafik soll den Zusammenhang zwischen Marketing und den anderen Unternehmensfunktionen veranschaulichen.\\n\\n## Vielfalt der Marketing-Definitionen\\nMarketing wird je nach Betrachtungsperspektive unterschiedlich definiert. Christian Homburg und Harley Krohmer führen bei der Bestimmung des Marketingbegriffs die drei zentralen historischen Marketingdefinitionen zu einer integrativen Marketingdefinition zusammen: \\n\\n### Aktivitätsorientierte Definition\\nEine aktivitätsorientierte Marketingdefinition versteht Marketing im Kern als ein Bündel marktgerichteter Unternehmensaktivitäten. Allgemeiner gefasst kann man in diesem Zusammenhang Marketing als einen Prozess der Planung und Durchführung des Konzeptes, des Preismanagements, der Werbeaktivitäten und des Vertriebs von Ideen, Gütern und Dienstleistungen, mit dem Zweck einen Austausch zu erreichen, der die Wünsche von Individuen und Organisationen befriedigt, bezeichnen. Die aktivitätsorientierte Definition entstand in den 1970er Jahren und wurde sehr stark durch die Entwicklung und Betonung des Marketing-Mix geprägt.\\n\\n### Beziehungsorientierte Definition\\nDie beziehungsorientierte Marketingdefinition legt den Schwerpunkt auf die Zielsetzung des Marketings, Kundenbeziehungen aufzubauen, zu erhalten und zu stärken, und zwar mithilfe von gegenseitigem Austausch und der Erfüllung von Versprechen (und somit dem Aufbau von Vertrauen). Die beziehungsorientierte Definition ersetzt jedoch keineswegs die aktivitätsorientierte Definition, sondern wirkt in Ergänzung zu ihr. Entstanden ist sie Ende der 1980er Jahre im Zusammenhang mit Relationship Marketing, die damals die Fokussierung auf die einzelnen Transaktionen mit dem Kunden zugunsten der Fokussierung auf die nachhaltigen Beziehungen mit dem Kunden ablöste.\\n\\n### Führungsorientierte Definition\\nDie führungsorientierte Marketingdefinition sieht Marketing als „bewusst marktorientierte Führung des gesamten Unternehmens [oder auch als] marktorientiertes Entscheidungsverhalten in der Unternehmung“ (Meffert, 2000). Wichtig sind bei dieser Definition insbesondere die unternehmensinternen Rahmenbedingungen, die die Ausrichtung der Unternehmensaktivitäten am Markt maßgeblich prägen; somit beinhaltet diese Definition sowohl das Konzept des Marketing-Mix, Aspekte der Marktimplementierung, den Gedanken der marktorientierten Unternehmensführung und des Relationship Marketing, was erklärt, wieso diese Definition als Ergänzung zu den beiden erstgenannten gesehen wird. Entwickelt wurde die führungsorientierte Definition in den 1980er Jahren, wonach sie jedoch erst in den 1990er Jahren eine wissenschaftliche Durchdringung erfahren hat. \\n\\n### Integrative Marketingdefinition\\nDie integrative Marketingdefinition von Homburg/Krohmer sieht Marketing als ein Konzept, das im Wesentlichen zwei Facetten hat, eine unternehmensinterne und eine unternehmensexterne:\\n- Die *unternehmensexterne Facette* sieht dabei Marketing als die Konzeption und Durchführung marktbezogener Aktivitäten eines Anbieters bezüglich (potenzieller) Nachfrager seiner Produkte. Solche marktbezogenen Aktivitäten beinhalten in diesem Kontext sowohl die systematische Informationsgewinnung über Marktgegebenheiten als auch die Gestaltung des Marketing-Mix.\\n- Für die *unternehmensinterne Facette* wiederum besteht Marketing aus der Schaffung der Voraussetzungen im Unternehmen für die Durchführung der marktbezogenen Aktivitäten. Dies beinhaltet insbesondere die Führung des Unternehmens nach der Leitidee der Marktorientierung.\\n\\nBeide Ansatzpunkte des Marketings zielen hierbei auf die *optimale Gestaltung* von Kundenbeziehungen im Sinne der Ziele des Unternehmens ab.\\n\\n### Alternative Marketingdefinitionen und deren Entstehungsprozess\\nPhilip Kotler, amerikanischer Wirtschaftswissenschaftler und Professor für Marketing an der Kellogg School of Management der Northwestern University, definiert Marketing wie folgt:\\n\\n> „Marketing ist ein Prozess im Wirtschafts- und Sozialgefüge, durch den Einzelpersonen und Gruppen ihre Bedürfnisse und Wünsche befriedigen, indem sie Produkte und andere Dinge von Wert erstellen, anbieten und miteinander austauschen.“\\n\\nEine über die funktionale Perspektive hinausgehende Definition versteht darunter die kunden- und marktorientierte Unternehmensführung zur Erreichung der Unternehmensziele.\\n\\n> „Marketing ist die konzeptionelle, bewusst marktorientierte Unternehmensführung, die sämtliche Unternehmensaktivitäten an den Bedürfnissen gegenwärtiger und potentieller Kunden ausrichtet, um die Unternehmensziele zu erreichen.“\\n\\nIn neueren Publikationen wird Marketing beispielsweise als Management komparativer Konkurrenzvorteile unter Nutzung der Marketinginstrumente verstanden.\\n\\n2004 ist die American Marketing Association (AMA) dazu übergegangen, ihre 20\\nJahre alte Definition zu modernisieren und vom Postulat einer unidirektionalen *Promotion* zum dialogorientierten Begriff des Kundenbeziehungsmanagements zu wechseln. Im Hinblick auf die neu hervorgehobene Konsumentenzentrierung wurden nicht nur die Interessen des Unternehmens, sondern die Interessen sämtlicher Stakeholder als Ziel des Marketingprozesses fokussiert. Damit zieht in die allgemeine Lehrmeinung der von den Investitionsgüteranbietern propagierte übergreifende Ansatz ein. So wird Marketing als „organisierende Funktion und Prozessbündel gesehen, um Werte auf eine Art und Weise für Kunden und Kundenbeziehungen zu schaffen, kommunizieren und bereitzustellen, sodass die Organisation und ihre Stakeholder davon profitieren“. Eine neuere Definition der AMA vom Oktober 2007 lautet: “Marketing is the activity, set of institutions, and processes for creating, communicating, delivering, and exchanging offerings that have value for customers, clients, partners, and society at large.”\\n\\nViele Marketingwissenschaftler sehen Marketing als eine im Kern unternehmerische Denkhaltung. Demnach kann „Marketing als Ausdruck eines marktorientierten unternehmerischen Denkstils gesehen werden, der sich durch schöpferische, systematische und zuweilen auch aggressive Note auszeichnet.“\\n\\nIn der Vision des Deutschen Marketing-Verbandes wird Marketing „als eine marktorientierte Unternehmensführung verstanden, die alle relevanten Unternehmensaktivitäten auf die Wünsche und Bedürfnisse von Anspruchsgruppen ausrichtet.“\\n\\nEinem aktiven Marketing kommt besonders bei Käufermärkten, d. h. Märkte mit einem deutlichen Angebotsüberhang, mit der Bedingung Angebot zu Nachfrage im Gegensatz zur Orientierung an früher verfolgten rein unternehmensinternen Zielen und Gegebenheiten wie Produktionskapazitäten eine erhöhte Bedeutung zu.\\n\\nDen Besonderheiten des Handels entsprechend hat Schenk für das Handelsmarketing einen über die bloße Kundenorientierung hinausgehenden Vier-Märkte-Ansatz entwickelt: „Für das Handelsmarketing sind die Handelsbetriebe Subjekte eigener Marketingstrategien und -taktiken, und zwar nicht nur in der Ausrichtung auf Absatzmärkte, sondern auf alle vier Märkte des Handelsbetriebs (Absatzmarkt, Beschaffungsmarkt, Konkurrenzmarkt, interner Markt).“\\n\\nAuch außerhalb der Unternehmenswelt nutzen mittlerweile Non-Profit-Organisationen („Non-Profit-Marketing“) Marketingtechniken. Letztlich erscheint eine exakte Definition oder Abgrenzung des Marketing-Begriffs für die BWL nicht notwendig, solange es einen grundlegenden Konsens über die wichtigsten Aufgaben des Marketing in Literatur und Praxis gibt.\\n\\n"}	\N	{"type": "Note"}	\N
247fde31-70d8-ab02-4107-c801b34cf06c	{"type": "Markdown", "content": "Vorbereitung"}	\N	{"type": "Stage"}	\N
247fde32-f0e1-2703-4107-c7e11e9accd1	{"type": "Markdown", "content": "1. Meilenstein"}	\N	{"type": "Stage"}	\N
247fde34-76b8-1d04-4107-c5f2ad2add9d	{"type": "Markdown", "content": "2. Meilenstein"}	\N	{"type": "Stage"}	\N
247fde35-a442-6105-4107-c753300cd7a2	{"type": "Markdown", "content": "Nachbereitung"}	\N	{"type": "Stage"}	\N
247fde4c-521b-8a06-4107-c61c216403e3	{"type": "Markdown", "content": "Template in: :male-teacher: Co..."}	\N	{"type": "Task"}	\N
247fde56-c887-4c08-4107-c81b7d663fee	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
247fde63-757d-090a-4107-c6c3456ebfd9	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
247fde84-68e7-0c12-4107-c7d0a05e4619	{"type": "RelativeDate", "content": 604800000}	\N	{"type": "Neutral"}	\N
247fde8b-7971-3d14-4107-c5c08b0ecb05	{"type": "RelativeDate", "content": 3628800000}	\N	{"type": "Neutral"}	\N
247fdf80-e982-5902-4107-c5d25fc23e64	{"type": "Markdown", "content": "Aix Coaching GmbH"}	\N	{"type": "Neutral"}	\N
247fdf84-cb21-3e04-4107-c789ff03ff30	{"type": "Decimal", "content": 52070.0}	\N	{"type": "Neutral"}	\N
247fdf89-b68c-e206-4107-c6a1b0b62a22	{"type": "Markdown", "content": "Aachen"}	\N	{"type": "Neutral"}	\N
247fdf8e-e024-1308-4107-c73c89ce8113	{"type": "Markdown", "content": "Jülicher Str. 72a"}	\N	{"type": "Neutral"}	\N
247fdf96-79d5-de0a-4107-c6cea1e0d31e	{"type": "Markdown", "content": "Julius Elias"}	\N	{"type": "Neutral"}	\N
247fdf99-5629-510c-4107-c7319536f108	{"type": "Markdown", "content": "Johannes Karoff"}	\N	{"type": "Neutral"}	\N
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	{"type": "Markdown", "content": "Felix Dietze"}	\N	{"type": "Neutral"}	\N
247fe208-389c-6310-4107-c61a7cd9f32e	{"type": "Markdown", "content": "Team-Template in: 2. Meilenstein"}	\N	{"type": "Task"}	{"{\\"type\\": \\"List\\"}","{\\"type\\": \\"Chat\\"}"}
247fe211-5156-6011-4107-c7350dc8125b	{"type": "Markdown", "content": "Weiter Aufgabe für den 2. MS"}	\N	{"type": "Task"}	\N
247fe220-dc39-a012-4107-c739796d8a4b	{"type": "Markdown", "content": "Hey,\\nihr erhaltet in Kürze die Zusammenfassung eurer Ergebnisse im 1. Meilenstein :wink:"}	\N	{"type": "Message"}	\N
247fe22e-a84f-1113-4107-c6e3d4b4ba33	{"type": "Markdown", "content": "Coach-Template in: Nachbereitung"}	\N	{"type": "Task"}	\N
247fe233-c8e3-a814-4107-c80cd04e0903	{"type": "Markdown", "content": "Abschlussbericht"}	\N	{"type": "Task"}	\N
247fe006-fe1f-840f-4107-c731ee179275	{"type": "Markdown", "content": "Coach-Template in: Vorbereitung"}	\N	{"type": "Task"}	\N
247fe11a-805f-5f01-4107-c7122b2928ba	{"type": "Markdown", "content": "In Bearbeitung"}	\N	{"type": "Stage"}	\N
247fe121-c144-5302-4107-c80d5cce3ad7	{"type": "Markdown", "content": "Erledigt"}	\N	{"type": "Stage"}	\N
247fe132-8869-4a03-4107-c5d95c5d0ed7	{"type": "Markdown", "content": "Eure erste Aufgabe: Erstellt ein Profil eures Unternehmens"}	\N	{"type": "Task"}	\N
247fe136-c956-e704-4107-c61ff8b187f7	{"type": "Markdown", "content": "Was sind eure Ziele?"}	\N	{"type": "Task"}	\N
247fe13c-d896-ce05-4107-c6a5d3682d1a	{"type": "Markdown", "content": "Was ist euer Zielgruppe?"}	\N	{"type": "Task"}	\N
247fe147-39fc-7e06-4107-c767e8d20152	{"type": "Markdown", "content": "Was ist eure Philosophie?"}	\N	{"type": "Task"}	\N
247fe151-2559-b307-4107-c65929aaaf37	{"type": "Markdown", "content": "Kontakt mit Ansprechpartner aufnehmen"}	\N	{"type": "Task"}	\N
247fe176-2dd7-dd08-4107-c78bba693725	{"type": "Markdown", "content": "Kontakt zum Kunden und Bestimmung der Teilnehmer"}	\N	{"type": "Task"}	\N
247fe18e-0f70-4809-4107-c7edec97f3de	{"type": "Markdown", "content": "Coach-Template in: 1. Meilenstein"}	\N	{"type": "Task"}	\N
247fde2e-90ea-9401-4107-c68110e68c92	{"type": "Markdown", "content": ":male-teacher: Coaching"}	\N	{"type": "Project"}	{"{\\"type\\": \\"Kanban\\"}","{\\"type\\": \\"Table\\", \\"roles\\": [{\\"type\\": \\"Task\\"}]}"}
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	{"type": "Markdown", "content": "Eure Aufgabe im 1. Meilenstein"}	\N	{"type": "Task"}	\N
247fe31d-ac85-8916-4107-c655528dc21e	{"type": "Placeholder", "targetType": {"data": "Markdown", "type": "Data"}}	\N	{"type": "Neutral"}	\N
247fe1b0-2f97-870b-4107-c6435b305b30	{"type": "Markdown", "content": "Team-Template in: 1. Meilenstein"}	\N	{"type": "Task"}	{"{\\"type\\": \\"List\\"}"}
247fe1dd-1eaa-7c0d-4107-c7e05e391294	{"type": "Markdown", "content": "Coach-Template in: 2. Meilenstein"}	\N	{"type": "Task"}	\N
247fe197-be08-be0a-4107-c617e5830fe1	{"type": "Markdown", "content": "Einführungspräsentation"}	\N	{"type": "Task"}	\N
247fe200-95b4-bc0e-4107-c7c9f99d9676	{"type": "Markdown", "content": "Erste Schulung durchführen"}	\N	{"type": "Task"}	\N
247fe206-0192-430f-4107-c7a480550d5b	{"type": "Markdown", "content": "Zusammenfassung der MS1 Ergebnisse"}	\N	{"type": "Task"}	\N
247fe3ab-444d-fe00-1531-1aba7f4ff6f2	{"name": "", "type": "User", "revision": 0, "isImplicit": true}	restricted	{"type": "Message"}	\N
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"name": "Juliane", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"name": "Andi (Manager)", "type": "User", "revision": 0, "isImplicit": false}	restricted	{"type": "Message"}	\N
247fe499-013f-9100-1531-1bf25ba12bee	{"name": "Daniel", "type": "User", "revision": 1, "isImplicit": false}	restricted	{"type": "Message"}	\N
247fe03e-e576-ec11-4107-c803db410fc0	{"type": "Markdown", "content": "Hey ${woost.parent.reverseField.field.Ansprechpartner} :wave:\\n\\nIch bin ${woost.parent.reverseField.assignee}, euer Coach für die nächsten 5 Wochen. Hier ist euer persönlicher Arbeitsbereich. Im Kanban Board habe ich eure Aufgaben für die Vorbereitung angelegt.\\n\\nAlle relevanten Informationen findet ihr in den Notizen (der dritte Tab).\\n\\nIhr könnt mir sonst auch jederzeit hier Fragen stellen.\\n\\nCheers\\n${woost.parent.reverseField.assignee}"}	\N	{"type": "Message"}	\N
247fe011-7658-9810-4107-c8227f4c6905	{"type": "Markdown", "content": "Woostspace ${woost.original}"}	\N	{"type": "Task"}	{"{\\"type\\": \\"Chat\\"}","{\\"type\\": \\"Kanban\\"}","{\\"type\\": \\"Content\\"}"}
247fe066-c5fa-7e12-4107-c7ee3ad94efa	{"type": "Markdown", "content": "<div> <img style=\\"width: 100%;\\" src=https://woost.space/wp-content/uploads/2019/06/Woost_Website_backdrop_video_NEW_4.png> </div> <div style=\\"padding: 10px 80px; width: 100%;\\"> <img style=\\"position: relative; margin-top: -80px;\\" src=https://woost.space/wp-content/uploads/2019/03/cropped-logo_large-192x192.png> </div> <div style=\\"padding: 10px 80px; width: 100%; color: rgb(55, 53, 47); font-size: 16px; font-family: Lato, Dejavu Sans, Helvetica;\\"> <div style=\\"padding: 20px 0px;\\"> <h1>Coaching by <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Unternehmen}</span></h1> <div style=\\"display: flex;\\"> <div style=\\"display: flex; width: 200px; color: rgba(55, 53, 47, 0.6);\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 16px; fill: rgba(55, 53, 47, 0.4);\\">:paperclip:</div> <div>Logo</div> </div> <div style=\\"width: 100%;\\"> <img src=https://woost.space/wp-content/uploads/2019/03/cropped-logo_large-192x192.png style=\\"max-height: 24px;\\"> </div> </div> <div style=\\"display: flex;\\"> <div style=\\"display: flex; width: 200px; color: rgba(55, 53, 47, 0.6);\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 16px; color: rgba(55, 53, 47, 0.4);\\">:calendar:</div> <div>Timeslot</div> </div> <div style=\\"width: 100%;\\"><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.field.Startzeitpunkt}</span> - <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.field.Endzeitpunkt}</span></div> </div> </div> <hr style=\\"border: 0; height: 1px; background-image: linear-gradient(to right, rgba(0,0,0,0), rgba(55, 53, 47, 0.2), rgba(0,0,0,0));\\"> <div style=\\"display: flex; flex-wrap: wrap; padding: 80px 0px 20px;\\"> <div style=\\"width: 40%; min-width: 240px; background: rgba(235, 236, 237, 0.3); padding: 20px; margin-right: 50px; display: flex;\\"> <div style=\\"margin-right: 10px\\">:wave:</div> <div style=\\"width: 100%; padding-right: 10px;\\"> <p>Hallo <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField}</span> und Herzlich Willkommen zu unserem gemeinsamen Coaching! Ich bin <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.assignee}</span> von <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Unternehmen}</span> und werde in den nächsten Wochen euer Ansprechpartner für das Coaching sein.</p> <p>Diese Informationsseite soll euch eine möglichst klare Vorstellung davon geben, was euch im Coaching erwartet. Wir haben uns Mühe gegeben die Informationsseite kurz und präzise zu gestalten ohne dabei Fragen offen zu lassen. Bitte schaut überall mal rein, damit wir alle auf dem selben Stand sind. :tada:</p> <p>Sollte trotz des Informationsseite noch irgendetwas unklar sein, meldet euch gerne bei mir unter <a href=mailto:${woost.parent.reverseField.assignee}@aixcoaching.de><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.assignee}</span>@aixcoaching.de</a></p> </div> </div> <div style=\\"width: 40%; padding: 20px; margin-left: 50px; display: flex; flex-direction: column; align-items: center;\\"> <img style=\\"width: 150px\\" src=https://woost.space/wp-content/uploads/2019/04/Julius_quadrat-300x300.png> <img style=\\"width: 150px\\" src=https://woost.space/wp-content/uploads/2019/04/Johannes_quadrat-300x300.png> <img style=\\"width: 150px\\" src=https://woost.space/wp-content/uploads/2019/04/Felix_quadrat-300x300.png> </div> </div> <div style=\\"padding: 80px 0px 20px;\\"> <h2>Contents</h2> <div style=\\"background: rgba(235, 236, 237, 0.3); margin: 10px 0 10px; padding: 20px; display: flex;\\"> <div style=\\"margin-right: 10px\\">:bulb:</div> <div> <p>In jedem der folgenden 4 Inhaltspunkte ist eine kleine Unterseite versteckt. Für eine besseres Verständnis empfiehlt es sich die Seiten der Reihe nach anzuschauen. :wink:</p> </div> </div> <hr style=\\"border: 0; height: 1px; background-image: linear-gradient(to right, rgba(0,0,0,0), rgba(55, 53, 47, 0.2), rgba(0,0,0,0));\\"> <div style=\\"padding-top: 20px;\\"> <div style=\\"display: flex; line-height: 1.5;\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 20px; color: rgba(55, 53, 47, 0.8);\\">:page_facing_up:</div> <div>1. Überblick über die 5 Wochen</div> </div> <div style=\\"display: flex; line-height: 1.5;\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 20px; color: rgba(55, 53, 47, 0.8);\\">:page_facing_up:</div> <div>2. Was bedeutet Coaching?</div> </div> <div style=\\"display: flex; line-height: 1.5;\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 20px; color: rgba(55, 53, 47, 0.8);\\">:page_facing_up:</div> <div>3. Die Workshop Wochen?</div> </div> <div style=\\"display: flex; line-height: 1.5;\\"> <div style=\\"flex-shrink: 0; margin-right: 10px; height: 20px; color: rgba(55, 53, 47, 0.8);\\">:page_facing_up:</div> <div>4. Weiteres Wissenswertes zum Coaching</div> </div> </div> </div> <div style=\\"padding: 80px 0px 20px;\\"> <h2>In a Nutshell</h2> <p style=\\"color: rgba(55, 53, 47, 0.6)\\">Hier findet ihr alle logistische und organisatorische Informationen auf einen Blick</p> <hr style=\\"border: 0; height: 1px; background-image: linear-gradient(to right, rgba(0,0,0,0), rgba(55, 53, 47, 0.2), rgba(0,0,0,0));\\"> <div style=\\"display: flex; padding-top: 20px; flex-wrap: wrap;\\"> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:clock2:</div> <div><span style=\\"font-weight: bold;\\">Zeitraum</span></div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\"><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.field.Startzeitpunkt}</span> - <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.field.Endzeitpunkt}</span></div> </div> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:memo:</div> <div><span style=\\"font-weight: bold;\\">Herausforderung / Problemstellung</span></div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\">TBA</div> </div> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:bar_chart:</div> <div><span style=\\"font-weight: bold;\\">Ergebnisse</div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\">Die Dateien & Links werden hier nach dem Coaching festgehalten</span></div> </div> </div> <div style=\\"display: flex; padding-top: 20px; flex-wrap: wrap;\\"> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:man-woman-boy:</div> <div><span style=\\"font-weight: bold;\\">Teilnehmer von <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Unternehmen}</span></span></div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\"> <ul> <li><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Coach1}</span></li> <li><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Coach2}</span></li> <li><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Coach3}</span></li> </ul> </div> </div> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:busts_in_silhouette:</div> <div><span style=\\"font-weight: bold;\\">Teilnehmer von <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField}</span></span></div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\"> <ul> ${woost.parent.reverseField.field.Teilnehmer.join(\\"\\", \\"<li><span style='font-weight: bold; color: #6636b7;'>\\", \\"</span></li>\\")} </ul> </div> </div> <div style=\\"width: 30%; min-width: 200px; padding: 30px 20px;\\"> <div style=\\"display: flex;\\"> <div style=\\"margin-right: 10px;\\">:round_pushpin:</div> <div><span style=\\"font-weight: bold;\\">Location</span></div> </div> <div style=\\"color: rgba(55, 53, 47, 0.6)\\"> <p><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Unternehmen}</span> Office<br><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Straße}</span><br><span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Plz}</span> <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Stadt}</span></p> </div> </div> </div> </div> <div style=\\"padding: 80px 0px 20px;\\"> <hr style=\\"border: 0; height: 1px; background-image: linear-gradient(to right, rgba(0,0,0,0), rgba(55, 53, 47, 0.2), rgba(0,0,0,0));\\"> All right's reserved <span style=\\"font-weight: bold; color: #6636b7;\\">${woost.parent.reverseField.parent.field.Unternehmen}</span> :copyright: 2019 </div> </div>"}	\N	{"type": "Note"}	\N
\.


--
-- Data for Name: node_can_access_mat; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.node_can_access_mat (nodeid, userid, complete) FROM stdin;
246dedbb-a4e5-4f01-0beb-c446b4802a70	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def1f-16b9-bf02-0beb-c3f1824d4e88	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def1f-16b9-bf02-0beb-c3f1824d4e88	246dedc9-7a4a-3d00-0beb-c52921a63779	t
246def22-4558-2803-0beb-c3cd98c524d9	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246def22-4558-2803-0beb-c3cd98c524d9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246def22-4558-2803-0beb-c3cd98c524d9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a10-92b4-2e07-3f11-241b79852233	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def24-b940-a804-0beb-c31ab1abee87	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def24-b940-a804-0beb-c31ab1abee87	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246def24-b940-a804-0beb-c31ab1abee87	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246def24-b940-a804-0beb-c31ab1abee87	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246def24-b940-a804-0beb-c31ab1abee87	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def7d-10f5-d605-0beb-c3cc4fdeea75	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246def7d-10f5-d605-0beb-c3cc4fdeea75	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6375-2ab2-9f01-3f11-24413ca87efd	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6375-2ab2-9f01-3f11-24413ca87efd	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e6375-2ab2-9f01-3f11-24413ca87efd	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e6375-2ab2-9f01-3f11-24413ca87efd	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e6375-2ab2-9f01-3f11-24413ca87efd	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a92-5692-5e13-3f11-2614592c761c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a92-5692-5e13-3f11-2614592c761c	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a92-5692-5e13-3f11-2614592c761c	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a92-5692-5e13-3f11-2614592c761c	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a92-5692-5e13-3f11-2614592c761c	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a10-92b4-2e07-3f11-241b79852233	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a10-92b4-2e07-3f11-241b79852233	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246def70-4f60-8b03-0beb-c4e0e34528ce	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246def70-4f60-8b03-0beb-c4e0e34528ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def6a-9639-8001-0beb-c2b7d2e661e4	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246def6a-9639-8001-0beb-c2b7d2e661e4	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5a10-92b4-2e07-3f11-241b79852233	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a10-92b4-2e07-3f11-241b79852233	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a2a-6f45-610b-3f11-249d1a2a7190	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a2a-6f45-610b-3f11-249d1a2a7190	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a2a-6f45-610b-3f11-249d1a2a7190	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a2a-6f45-610b-3f11-249d1a2a7190	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a2a-6f45-610b-3f11-249d1a2a7190	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5960-e42d-9806-3f11-251c314abb60	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5960-e42d-9806-3f11-251c314abb60	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5960-e42d-9806-3f11-251c314abb60	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5960-e42d-9806-3f11-251c314abb60	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5960-e42d-9806-3f11-251c314abb60	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a37-f2c5-dd0f-3f11-25b0860e15c6	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b7b-4d09-5e2c-3f11-25e0c59e2c49	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5cec-6a03-5607-3f11-2400c923c1b0	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5cec-6a03-5607-3f11-2400c923c1b0	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d31-a500-9c0d-3f11-25b18d58d025	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d31-a500-9c0d-3f11-25b18d58d025	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6137-64a0-db07-3f11-26062f9907e8	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6137-64a0-db07-3f11-26062f9907e8	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5b40-b0b6-ac28-3f11-24bbc9148469	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5b40-b0b6-ac28-3f11-24bbc9148469	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b40-b0b6-ac28-3f11-24bbc9148469	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5af8-7876-0a20-3f11-2513978b8f75	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5af8-7876-0a20-3f11-2513978b8f75	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5af8-7876-0a20-3f11-2513978b8f75	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e6598-bc99-e502-3f11-23e4066f9d5c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6598-bc99-e502-3f11-23e4066f9d5c	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e6598-bc99-e502-3f11-23e4066f9d5c	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6598-bc99-e502-3f11-23e4066f9d5c	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e6598-bc99-e502-3f11-23e4066f9d5c	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e6596-b6d1-3101-3f11-24a80f73c202	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e6596-b6d1-3101-3f11-24a80f73c202	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e6596-b6d1-3101-3f11-24a80f73c202	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6596-b6d1-3101-3f11-24a80f73c202	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6596-b6d1-3101-3f11-24a80f73c202	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f7091-3c0c-e801-3f11-496d471ea961	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f7091-3c0c-e801-3f11-496d471ea961	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f7091-3c0c-e801-3f11-496d471ea961	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f7091-3c0c-e801-3f11-496d471ea961	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7091-3c0c-e801-3f11-496d471ea961	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f7092-6b17-9b02-3f11-47fed8991d27	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f7092-6b17-9b02-3f11-47fed8991d27	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f7092-6b17-9b02-3f11-47fed8991d27	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f7092-6b17-9b02-3f11-47fed8991d27	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7092-6b17-9b02-3f11-47fed8991d27	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a2c-7aa9-4d0c-3f11-260b798c2e30	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e59f1-cc84-a905-3f11-255b12ce45d4	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e59f1-cc84-a905-3f11-255b12ce45d4	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def43-bdb5-5b0b-0beb-c3568a4a0a45	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df082-abe5-3002-0beb-c47cf1ef8b08	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df082-abe5-3002-0beb-c47cf1ef8b08	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-ea84-1102-3f11-252e6f15e681	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-ea84-1102-3f11-252e6f15e681	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e580e-7a90-e902-3f11-245128873461	246dedd1-194a-5d00-0beb-c5195068e4cc	t
246e580e-7a90-e902-3f11-245128873461	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df09c-af4e-6705-0beb-c37d90e281e1	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df09c-af4e-6705-0beb-c37d90e281e1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0c5-a8ef-f108-0beb-c31717c4359a	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0c5-a8ef-f108-0beb-c31717c4359a	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6172-349c-1101-3f11-23b5e165928e	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e6172-349c-1101-3f11-23b5e165928e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6172-349c-1101-3f11-23b5e165928e	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e6172-349c-1101-3f11-23b5e165928e	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6172-349c-1101-3f11-23b5e165928e	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a1c-f2c6-eb0a-3f11-23979f076ced	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5b23-5d72-4025-3f11-2477952948a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b23-5d72-4025-3f11-2477952948a6	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b23-5d72-4025-3f11-2477952948a6	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a55-0c93-4b12-3f11-24b93a8e1e6c	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5b44-be95-8b29-3f11-24e0646555ce	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b44-be95-8b29-3f11-24e0646555ce	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5b44-be95-8b29-3f11-24e0646555ce	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3212-3f11-25a04fd48f11	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3212-3f11-25a04fd48f11	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-5044-3212-3f11-25a04fd48f11	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b1-ca5e-7316-3f11-240f0836267b	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61b1-ca5e-7316-3f11-240f0836267b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61b1-ca5e-7316-3f11-240f0836267b	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5b08-68f6-a123-3f11-25f5adf1dced	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5b08-68f6-a123-3f11-25f5adf1dced	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b08-68f6-a123-3f11-25f5adf1dced	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246fed72-54b1-cc01-3f11-480f760fe40e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246fed72-54b1-cc01-3f11-480f760fe40e	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246fed72-54b1-cc01-3f11-480f760fe40e	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0d3-1369-3309-0beb-c2bf5bd0e8ae	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def2f-9ee6-2107-0beb-c2bd2be44a1a	246dedd1-194a-5d00-0beb-c5195068e4cc	t
246e59f1-cc84-a905-3f11-255b12ce45d4	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e59f1-cc84-a905-3f11-255b12ce45d4	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59f1-cc84-a905-3f11-255b12ce45d4	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e59fc-6246-4406-3f11-259301538327	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e580f-f932-9703-3f11-24ec645ceced	246dedd1-194a-5d00-0beb-c5195068e4cc	t
246e580f-f932-9703-3f11-24ec645ceced	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0e9-03d8-780c-0beb-c51f81463c9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0e9-03d8-780c-0beb-c51f81463c9d	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246df0e9-03d8-780c-0beb-c51f81463c9d	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246df0e9-03d8-780c-0beb-c51f81463c9d	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246df0e9-03d8-780c-0beb-c51f81463c9d	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0e9-ed9b-f70d-0beb-c3962616de23	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59fc-6246-4406-3f11-259301538327	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59fc-6246-4406-3f11-259301538327	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e59fc-6246-4406-3f11-259301538327	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e59fc-6246-4406-3f11-259301538327	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a18-a522-6f09-3f11-250bca87bdec	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a18-a522-6f09-3f11-250bca87bdec	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a18-a522-6f09-3f11-250bca87bdec	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a18-a522-6f09-3f11-250bca87bdec	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a18-a522-6f09-3f11-250bca87bdec	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a2d-7070-440d-3f11-2391a98a7349	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a2d-7070-440d-3f11-2391a98a7349	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a2d-7070-440d-3f11-2391a98a7349	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b1e-e0f3-7024-3f11-24efbdc303f4	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5a2d-7070-440d-3f11-2391a98a7349	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a2d-7070-440d-3f11-2391a98a7349	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5a2e-47c8-0b0e-3f11-246cc4033b70	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0e9-ed9b-f70d-0beb-c3962616de23	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246df0e9-ed9b-f70d-0beb-c3962616de23	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246df0e9-ed9b-f70d-0beb-c3962616de23	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246df0e9-ed9b-f70d-0beb-c3962616de23	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246df0ec-3945-a50e-0beb-c4518b8ef3b9	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def27-4148-9105-0beb-c4170f451769	246dedcf-0d99-8e00-0beb-c4352b0feb3a	t
246def27-4148-9105-0beb-c4170f451769	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df063-4821-c501-0beb-c30c29953475	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7093-9d89-b003-3f11-4836856dae24	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7093-9d89-b003-3f11-4836856dae24	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0d4-468e-af0a-0beb-c32aaa58952c	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0d4-468e-af0a-0beb-c32aaa58952c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7093-9d89-b003-3f11-4836856dae24	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f7093-9d89-b003-3f11-4836856dae24	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246df0d6-17fb-780b-0beb-c4873e10525b	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0d6-17fb-780b-0beb-c4873e10525b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e592a-56d3-b001-3f11-256967ea9c5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e592a-56d3-b001-3f11-256967ea9c5b	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f7093-9d89-b003-3f11-4836856dae24	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f7094-b3f3-ad04-3f11-4922ede189a3	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f7094-b3f3-ad04-3f11-4922ede189a3	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f7094-b3f3-ad04-3f11-4922ede189a3	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f7094-b3f3-ad04-3f11-4922ede189a3	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f7094-b3f3-ad04-3f11-4922ede189a3	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70a0-5bea-db05-3f11-49cad99286bd	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f70a0-5bea-db05-3f11-49cad99286bd	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f70a0-5bea-db05-3f11-49cad99286bd	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f70a0-5bea-db05-3f11-49cad99286bd	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f70a0-5bea-db05-3f11-49cad99286bd	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70ed-2549-db06-3f11-48b8a016a56b	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f70ed-2549-db06-3f11-48b8a016a56b	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f70ed-2549-db06-3f11-48b8a016a56b	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f70ed-2549-db06-3f11-48b8a016a56b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f70ed-2549-db06-3f11-48b8a016a56b	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70f1-9029-fb07-3f11-4991f55b98b6	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f70f1-9029-fb07-3f11-4991f55b98b6	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70f1-9029-fb07-3f11-4991f55b98b6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f70f1-9029-fb07-3f11-4991f55b98b6	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f70f1-9029-fb07-3f11-4991f55b98b6	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f70fd-07f9-a808-3f11-47854803fecc	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f70fd-07f9-a808-3f11-47854803fecc	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f70fd-07f9-a808-3f11-47854803fecc	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f70fd-07f9-a808-3f11-47854803fecc	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70fd-07f9-a808-3f11-47854803fecc	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246f70ff-74f9-c309-3f11-48dc20ca1c68	246dedd2-3468-e900-0beb-c4797c30fedf	t
246f70ff-74f9-c309-3f11-48dc20ca1c68	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246f70ff-74f9-c309-3f11-48dc20ca1c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246f70ff-74f9-c309-3f11-48dc20ca1c68	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246f70ff-74f9-c309-3f11-48dc20ca1c68	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a40-02cd-0611-3f11-243db530640f	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5a40-02cd-0611-3f11-243db530640f	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e5abe-b762-a616-3f11-259f98f05fd8	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5abe-b762-a616-3f11-259f98f05fd8	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5abe-b762-a616-3f11-259f98f05fd8	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617e-84c3-cf04-3f11-24ca856b8dc8	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617e-84c3-cf04-3f11-24ca856b8dc8	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617e-84c3-cf04-3f11-24ca856b8dc8	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617e-84c3-cf04-3f11-24ca856b8dc8	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617e-84c3-cf04-3f11-24ca856b8dc8	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246def6d-bc08-9102-0beb-c3b2aeeee6b1	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5a40-02cd-0611-3f11-243db530640f	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e5a40-02cd-0611-3f11-243db530640f	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5a40-02cd-0611-3f11-243db530640f	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db06-3f11-25ea7e718c4d	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db06-3f11-25ea7e718c4d	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-5fcf-db06-3f11-25ea7e718c4d	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db06-3f11-25ea7e718c4d	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db06-3f11-25ea7e718c4d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b6f-0e80-502a-3f11-249340305022	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b6f-0e80-502a-3f11-249340305022	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5b7a-005c-832b-3f11-23f35d51bc74	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b7a-005c-832b-3f11-23f35d51bc74	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5b91-c051-9e02-3f11-26196575e75b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5b91-c051-9e02-3f11-26196575e75b	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5cbd-06ba-fe05-3f11-24643fd81dd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5cbd-06ba-fe05-3f11-24643fd81dd2	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5ccb-5cab-7006-3f11-2608c9423927	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ccb-5cab-7006-3f11-2608c9423927	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e618a-9bdb-130e-3f11-240e34db8b23	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e618a-9bdb-130e-3f11-240e34db8b23	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e618a-9bdb-130e-3f11-240e34db8b23	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e618a-9bdb-130e-3f11-240e34db8b23	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e618a-9bdb-130e-3f11-240e34db8b23	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617e-84c3-cf05-3f11-256a564f317c	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617e-84c3-cf05-3f11-256a564f317c	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617e-84c3-cf05-3f11-256a564f317c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617e-84c3-cf05-3f11-256a564f317c	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617e-84c3-cf05-3f11-256a564f317c	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617e-84c3-cf03-3f11-259494710bd2	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617e-84c3-cf03-3f11-259494710bd2	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617e-84c3-cf03-3f11-259494710bd2	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617e-84c3-cf03-3f11-259494710bd2	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617e-84c3-cf03-3f11-259494710bd2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617e-84c3-cf02-3f11-243871f5d777	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61b4-c253-7a19-3f11-24a0f85e1a3d	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-5044-3211-3f11-2521cff85b61	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61a5-5044-3211-3f11-2521cff85b61	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3211-3f11-2521cff85b61	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e617e-84c3-cf02-3f11-243871f5d777	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617e-84c3-cf02-3f11-243871f5d777	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617e-84c3-cf02-3f11-243871f5d777	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617e-84c3-cf02-3f11-243871f5d777	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db08-3f11-23e68e60bf48	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db08-3f11-23e68e60bf48	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-5fcf-db08-3f11-23e68e60bf48	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-5fcf-db08-3f11-23e68e60bf48	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db08-3f11-23e68e60bf48	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db07-3f11-25529d531bf5	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-5fcf-db07-3f11-25529d531bf5	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5cf1-f5ea-1d08-3f11-254bbf0f7d7d	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d05-bb05-d709-3f11-23bf6f593df9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d05-bb05-d709-3f11-23bf6f593df9	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d0d-6723-5a0a-3f11-24ac972fb4a9	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d1f-7c8f-3c0c-3f11-24697d735ac2	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d3a-b785-9b0e-3f11-2424adf15a20	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d3a-b785-9b0e-3f11-2424adf15a20	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d62-32b8-b90f-3f11-24715d4a6f48	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d62-32b8-b90f-3f11-24715d4a6f48	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d62-cfe6-1b10-3f11-24a4ed39a889	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d62-cfe6-1b10-3f11-24a4ed39a889	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d65-a21d-0911-3f11-248b41f6cd32	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d65-a21d-0911-3f11-248b41f6cd32	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5d93-e077-3312-3f11-23efb628d24f	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5d93-e077-3312-3f11-23efb628d24f	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5dbb-fdbf-8414-3f11-26088435221f	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5dbb-fdbf-8414-3f11-26088435221f	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5bae-0514-bc04-3f11-257ec75b65c1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5bae-0514-bc04-3f11-257ec75b65c1	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6077-a142-6301-3f11-2399ab4d19b5	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6077-a142-6301-3f11-2399ab4d19b5	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e608f-5b4f-6302-3f11-24cf9a89be03	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e608f-5b4f-6302-3f11-24cf9a89be03	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6122-eab7-5305-3f11-24df71c6e1c2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-eab7-5305-3f11-24df71c6e1c2	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6122-eab7-5306-3f11-254480b90c6e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-eab7-5306-3f11-254480b90c6e	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6122-ea9d-b204-3f11-2554ab0b95d3	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-ea9d-b204-3f11-2554ab0b95d3	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e6122-ea84-1101-3f11-25d4add70289	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e6122-ea84-1101-3f11-25d4add70289	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db07-3f11-25529d531bf5	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db07-3f11-25529d531bf5	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db07-3f11-25529d531bf5	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db0b-3f11-2408b71a2684	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db0b-3f11-2408b71a2684	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db0b-3f11-2408b71a2684	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db0b-3f11-2408b71a2684	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-5fcf-db0b-3f11-2408b71a2684	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-5fcf-db0a-3f11-23ad06880e1f	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-5fcf-db0a-3f11-23ad06880e1f	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-5fcf-db0a-3f11-23ad06880e1f	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
247fe499-013f-9100-1531-1bf25ba12bee	247fe499-013f-9100-1531-1bf25ba12bee	t
246e617f-5fcf-db0a-3f11-23ad06880e1f	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db0a-3f11-23ad06880e1f	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db09-3f11-2502e35db6f2	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-5fcf-db09-3f11-2502e35db6f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-5fcf-db09-3f11-2502e35db6f2	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-5fcf-db09-3f11-2502e35db6f2	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-5fcf-db09-3f11-2502e35db6f2	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246def2b-5b11-4706-0beb-c4777a1eae11	246dedd0-2236-3900-0beb-c3131fbba3b8	t
246def2b-5b11-4706-0beb-c4777a1eae11	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def1b-8fe8-4501-0beb-c468922ff921	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def1b-8fe8-4501-0beb-c468922ff921	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def3f-d27a-160a-0beb-c4767631b609	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def3f-d27a-160a-0beb-c4767631b609	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-f82f-0d0c-3f11-25234559f1f6	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-f82f-0d0c-3f11-25234559f1f6	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-f82f-0d0c-3f11-25234559f1f6	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e617f-f82f-0d0c-3f11-25234559f1f6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e617f-f82f-0d0c-3f11-25234559f1f6	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-f82f-0d0d-3f11-23d0fad077c8	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e617f-f82f-0d0d-3f11-23d0fad077c8	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246def73-32cf-a504-0beb-c3a4ce0f0647	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def73-32cf-a504-0beb-c3a4ce0f0647	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e617f-f82f-0d0d-3f11-23d0fad077c8	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e617f-f82f-0d0d-3f11-23d0fad077c8	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e617f-f82f-0d0d-3f11-23d0fad077c8	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-505d-d315-3f11-2427f82d1a58	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-505d-d315-3f11-2427f82d1a58	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-505d-d315-3f11-2427f82d1a58	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b4-c253-7a1b-3f11-24550f613706	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b4-c253-7a1b-3f11-24550f613706	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61b4-c253-7a1b-3f11-24550f613706	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246df0fd-9ab0-c112-0beb-c2daba94f8cd	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246df0fd-2274-7011-0beb-c5026310961d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0fd-2274-7011-0beb-c5026310961d	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246df0fd-2274-7011-0beb-c5026310961d	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5ac6-32f2-2419-3f11-2400a6083c68	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ac6-32f2-2419-3f11-2400a6083c68	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5ac6-32f2-2419-3f11-2400a6083c68	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5ac8-3339-411a-3f11-24d6eff5be62	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5ac8-3339-411a-3f11-24d6eff5be62	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ac8-3339-411a-3f11-24d6eff5be62	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5adb-b1da-3b1d-3f11-261dea95c4f2	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246def3c-ca66-ce09-0beb-c4189521dfec	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def3c-ca66-ce09-0beb-c4189521dfec	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5aaa-03e8-7f15-3f11-24e5412142ac	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5aaa-03e8-7f15-3f11-24e5412142ac	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5aaa-03e8-7f15-3f11-24e5412142ac	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246df0f8-b5e0-9f0f-0beb-c361d12451f9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ac3-0955-2d17-3f11-2584d953ac42	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246df0fa-0226-f610-0beb-c3a35b86f228	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246df0fa-0226-f610-0beb-c3a35b86f228	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246df0fa-0226-f610-0beb-c3a35b86f228	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ac3-0955-2d17-3f11-2584d953ac42	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ac3-0955-2d17-3f11-2584d953ac42	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5ac3-7b29-3e18-3f11-23d89a57d139	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e592c-6ced-e702-3f11-23a4149259fc	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e592c-6ced-e702-3f11-23a4149259fc	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e593e-835a-1303-3f11-2403b117bcf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e593e-835a-1303-3f11-2403b117bcf7	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5ac3-7b29-3e18-3f11-23d89a57d139	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5ac3-7b29-3e18-3f11-23d89a57d139	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ae8-2b0d-d31f-3f11-260751da15f1	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5ae8-2b0d-d31f-3f11-260751da15f1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5ae8-2b0d-d31f-3f11-260751da15f1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5ad8-2c09-0b1c-3f11-25e0df1b2cbd	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5afd-bb2e-1622-3f11-25cafbc898ed	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5afd-bb2e-1622-3f11-25cafbc898ed	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5afd-bb2e-1622-3f11-25cafbc898ed	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e5b33-19ca-2e27-3f11-25db2be6f6fd	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3210-3f11-247569b3c4da	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61a5-5044-3210-3f11-247569b3c4da	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-5044-3210-3f11-247569b3c4da	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-502a-910f-3f11-2423d7ac2bd1	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-502a-910f-3f11-2423d7ac2bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-502a-910f-3f11-2423d7ac2bd1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b1-ca5e-7318-3f11-23d6d1b63629	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61b1-ca5e-7318-3f11-23d6d1b63629	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61b1-ca5e-7318-3f11-23d6d1b63629	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b4-c253-7a1a-3f11-251679c00cf7	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61b4-c253-7a1a-3f11-251679c00cf7	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b4-c253-7a1a-3f11-251679c00cf7	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-505d-d314-3f11-25160bc7db52	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-505d-d314-3f11-25160bc7db52	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-505d-d314-3f11-25160bc7db52	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246dedd2-3468-e900-0beb-c4797c30fedf	t
246df0bf-eb61-3a06-0beb-c42d83cf6eb6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e65a2-092f-a803-3f11-25841a2243a6	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e65a2-092f-a803-3f11-25841a2243a6	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e65a2-092f-a803-3f11-25841a2243a6	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e65a2-092f-a803-3f11-25841a2243a6	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246df08d-6316-d903-0beb-c32b118ae54f	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246df08d-6316-d903-0beb-c32b118ae54f	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e5aa8-28ac-1414-3f11-2406f6f2aac6	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e65a2-092f-a803-3f11-25841a2243a6	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e61b1-ca5e-7317-3f11-24f6fd78d1d1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3213-3f11-26087a823003	246dedca-929b-2d00-0beb-c3cb96965ce3	t
246e61a5-5044-3213-3f11-26087a823003	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e61a5-5044-3213-3f11-26087a823003	246dedcb-974c-d900-0beb-c4e3a7d0dff6	t
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e595e-5fd9-f404-3f11-23f6e8cb8bd1	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e595f-38e5-6c05-3f11-24ce4fa8e56e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59da-2c18-a901-3f11-255316ece461	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e59da-2c18-a901-3f11-255316ece461	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e59da-2c18-a901-3f11-255316ece461	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e59da-2c18-a901-3f11-255316ece461	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59da-2c18-a901-3f11-255316ece461	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e59e1-851e-2e02-3f11-23cc61231036	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246e59e1-851e-2e02-3f11-23cc61231036	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e59e1-851e-2e02-3f11-23cc61231036	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e59e1-851e-2e02-3f11-23cc61231036	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e59e1-851e-2e02-3f11-23cc61231036	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e59e2-824d-af03-3f11-23a005bd4630	246dedcc-c055-d000-0beb-c4dbf20d81d0	t
246e59e2-824d-af03-3f11-23a005bd4630	246dedd2-3468-e900-0beb-c4797c30fedf	t
246e59e2-824d-af03-3f11-23a005bd4630	246dedc6-243a-0a00-0beb-c4d86329b3b4	t
246e59e2-824d-af03-3f11-23a005bd4630	246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	t
246e59e2-824d-af03-3f11-23a005bd4630	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
246def39-9145-5c08-0beb-c2d1bfcb6705	246dedd2-3468-e900-0beb-c4797c30fedf	t
246def39-9145-5c08-0beb-c2d1bfcb6705	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde31-70d8-ab02-4107-c801b34cf06c	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde31-70d8-ab02-4107-c801b34cf06c	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe006-fe1f-840f-4107-c731ee179275	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe006-fe1f-840f-4107-c731ee179275	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde4c-521b-8a06-4107-c61c216403e3	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde4c-521b-8a06-4107-c61c216403e3	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde32-f0e1-2703-4107-c7e11e9accd1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde32-f0e1-2703-4107-c7e11e9accd1	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe18e-0f70-4809-4107-c7edec97f3de	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe18e-0f70-4809-4107-c7edec97f3de	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe1b0-2f97-870b-4107-c6435b305b30	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe1b0-2f97-870b-4107-c6435b305b30	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde34-76b8-1d04-4107-c5f2ad2add9d	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde34-76b8-1d04-4107-c5f2ad2add9d	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe1dd-1eaa-7c0d-4107-c7e05e391294	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe1dd-1eaa-7c0d-4107-c7e05e391294	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe208-389c-6310-4107-c61a7cd9f32e	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe208-389c-6310-4107-c61a7cd9f32e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde35-a442-6105-4107-c753300cd7a2	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde35-a442-6105-4107-c753300cd7a2	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe22e-a84f-1113-4107-c6e3d4b4ba33	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe22e-a84f-1113-4107-c6e3d4b4ba33	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe3c7-6d89-2900-1531-1b012f3f05b9	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
246ded7f-ae61-db00-0beb-c341f9e67fd0	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde2e-90ea-9401-4107-c68110e68c92	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde2e-90ea-9401-4107-c68110e68c92	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde63-757d-090a-4107-c6c3456ebfd9	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde63-757d-090a-4107-c6c3456ebfd9	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde84-68e7-0c12-4107-c7d0a05e4619	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde84-68e7-0c12-4107-c7d0a05e4619	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde8b-7971-3d14-4107-c5c08b0ecb05	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde8b-7971-3d14-4107-c5c08b0ecb05	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde56-c887-4c08-4107-c81b7d663fee	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde56-c887-4c08-4107-c81b7d663fee	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf80-e982-5902-4107-c5d25fc23e64	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf80-e982-5902-4107-c5d25fc23e64	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf84-cb21-3e04-4107-c789ff03ff30	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf84-cb21-3e04-4107-c789ff03ff30	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf89-b68c-e206-4107-c6a1b0b62a22	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf89-b68c-e206-4107-c6a1b0b62a22	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf8e-e024-1308-4107-c73c89ce8113	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf8e-e024-1308-4107-c73c89ce8113	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf9d-8511-ba0e-4107-c5bf9f2f9598	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf99-5629-510c-4107-c7319536f108	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf99-5629-510c-4107-c7319536f108	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fdf96-79d5-de0a-4107-c6cea1e0d31e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fdf96-79d5-de0a-4107-c6cea1e0d31e	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe151-2559-b307-4107-c65929aaaf37	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe151-2559-b307-4107-c65929aaaf37	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe176-2dd7-dd08-4107-c78bba693725	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe176-2dd7-dd08-4107-c78bba693725	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe197-be08-be0a-4107-c617e5830fe1	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe197-be08-be0a-4107-c617e5830fe1	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe1cc-d9c4-ba0c-4107-c69bde8fb534	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe200-95b4-bc0e-4107-c7c9f99d9676	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe200-95b4-bc0e-4107-c7c9f99d9676	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe206-0192-430f-4107-c7a480550d5b	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe206-0192-430f-4107-c7a480550d5b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe211-5156-6011-4107-c7350dc8125b	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe211-5156-6011-4107-c7350dc8125b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe220-dc39-a012-4107-c739796d8a4b	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe220-dc39-a012-4107-c739796d8a4b	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe233-c8e3-a814-4107-c80cd04e0903	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe233-c8e3-a814-4107-c80cd04e0903	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe31d-ac85-8916-4107-c655528dc21e	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe31d-ac85-8916-4107-c655528dc21e	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fde6c-5f5c-b20c-4107-c77ef0d2c9aa	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe011-7658-9810-4107-c8227f4c6905	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe011-7658-9810-4107-c8227f4c6905	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe011-7658-9810-4107-c8227f4c6905	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe132-8869-4a03-4107-c5d95c5d0ed7	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe132-8869-4a03-4107-c5d95c5d0ed7	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe03e-e576-ec11-4107-c803db410fc0	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe03e-e576-ec11-4107-c803db410fc0	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe03e-e576-ec11-4107-c803db410fc0	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe11a-805f-5f01-4107-c7122b2928ba	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe11a-805f-5f01-4107-c7122b2928ba	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe11a-805f-5f01-4107-c7122b2928ba	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe121-c144-5302-4107-c80d5cce3ad7	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe121-c144-5302-4107-c80d5cce3ad7	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe121-c144-5302-4107-c80d5cce3ad7	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe136-c956-e704-4107-c61ff8b187f7	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe136-c956-e704-4107-c61ff8b187f7	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe136-c956-e704-4107-c61ff8b187f7	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe13c-d896-ce05-4107-c6a5d3682d1a	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe13c-d896-ce05-4107-c6a5d3682d1a	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe13c-d896-ce05-4107-c6a5d3682d1a	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe147-39fc-7e06-4107-c767e8d20152	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
247fe147-39fc-7e06-4107-c767e8d20152	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe147-39fc-7e06-4107-c767e8d20152	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe066-c5fa-7e12-4107-c7ee3ad94efa	247fe499-013f-9100-1531-1bf25ba12bee	t
247fe066-c5fa-7e12-4107-c7ee3ad94efa	247fe3c7-6d89-2900-1531-1b012f3f05b9	t
247fe066-c5fa-7e12-4107-c7ee3ad94efa	246ded7f-ae61-db00-0beb-c341f9e67fd0	t
\.


--
-- Data for Name: oauthclient; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.oauthclient (userid, service, accesstoken) FROM stdin;
\.


--
-- Data for Name: password; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.password (userid, digest) FROM stdin;
246ded7f-ae61-db00-0beb-c341f9e67fd0	\\x243261243130245732524f72594e48483761307746505731494a7461653256574f363865497033486e69614b746678656461445272764c4c6a714832
246dedc6-243a-0a00-0beb-c4d86329b3b4	\\x24326124313024566573637a52476346654850686f556a42334c374b2e64784b37344270772e314a6f454c6763356258733254474b3677442f306165
246dedc9-7a4a-3d00-0beb-c52921a63779	\\x243261243130247465703246516e3332656e464a41326156633264354f686c745938693979437a4e51376b4b7768585446654d4a71644d482e757a6d
246dedca-929b-2d00-0beb-c3cb96965ce3	\\x2432612431302431654e74367a55374d6a44394567314138494e35546548327862496359504b324631326b2e6f5164524462787256546d5441427a69
246dedcb-974c-d900-0beb-c4e3a7d0dff6	\\x243261243130244e707238392e3349764b6c584a32515569497777684f6f49447a695a376b4f6e484438395a636746503661757964786c474c4d312e
246dedcc-c055-d000-0beb-c4dbf20d81d0	\\x243261243130244c7451656249714f374a552f435152784d2f7947334f7a41682e7533386e4a772f7273496c50683778385444762e7352616f645153
246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	\\x24326124313024754e7043765369535975733351584c666f2f3638704f5768335253454b573641365747783536794235344358786e4a677668566f6d
246dedcf-0d99-8e00-0beb-c4352b0feb3a	\\x24326124313024347439397359754e57436a50664258466c3463765465513951302f3675543830615949366442746f6656756d35434d6e4e62463447
246dedd0-2236-3900-0beb-c3131fbba3b8	\\x24326124313024306c344571524b622f37364c496f5a737779342f7065587168754778476571423273743345383845583858326e424863302f377336
246dedd1-194a-5d00-0beb-c5195068e4cc	\\x24326124313024422f79624c367379474a4d49316f36552e4a483556756965756134682e43585345342e42343878704935703052614d6253306e7875
246dedd2-3468-e900-0beb-c4797c30fedf	\\x2432612431302438534567754a4f4d6e4253345959726d67624253612e3845764b314258304466615a746d304c7258684479706b6c455a78512e4532
247fe3c7-6d89-2900-1531-1b012f3f05b9	\\x24326124313024317941534963594b4e5971542e6478756465445364756a436d6d6974622e6d4a45435233776f2f2f552f717178434f716a6d735053
247fe499-013f-9100-1531-1bf25ba12bee	\\x24326124313024514371393348447448633563486b70636374427530654558786a776e2f58654e7164426d50586f5362443479634f6b4f6d6d716175
\.


--
-- Data for Name: usedfeature; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.usedfeature (userid, feature, "timestamp") FROM stdin;
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchPageFromExpandedLeftSidebar"}	2019-08-15 13:33:48.202
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "ZoomIntoProject"}	2019-08-15 13:33:53.675
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "OpenProjectInRightSidebar"}	2019-08-15 13:34:02.283
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "EditProjectInRightSidebar"}	2019-08-15 13:34:33.427
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "OpenTaskInRightSidebar"}	2019-08-15 13:39:58.208
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "EditTaskInRightSidebar"}	2019-08-15 13:40:05.591
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchToKanbanInPageHeader"}	2019-08-15 13:42:25.451
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "EditColumnInKanban"}	2019-08-15 13:42:37.961
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "ZoomIntoTask"}	2019-08-15 14:01:36.017
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "FilterAutomationTemplates"}	2019-08-15 14:06:36.668
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateProjectFromExpandedLeftSidebar"}	2019-08-15 14:39:44.949
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateProject"}	2019-08-15 14:39:54.276
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "AddKanbanView"}	2019-08-15 14:39:54.284
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateColumnInKanban"}	2019-08-15 14:40:01.654
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateAutomationTemplate"}	2019-08-15 14:41:10.473
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "AddCustomFieldToTask"}	2019-08-15 14:41:37.149
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchToChatInRightSidebar"}	2019-08-15 15:01:36.097
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "AddChatView"}	2019-08-15 15:01:36.133
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchToKanbanInRightSidebar"}	2019-08-15 15:01:44.589
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "AddNotesView"}	2019-08-15 15:01:47.83
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateMessageInChat"}	2019-08-15 15:02:25.383
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateNoteInNotes"}	2019-08-15 15:04:07.204
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchToChatInPageHeader"}	2019-08-15 15:08:59.701
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateTaskInKanban"}	2019-08-15 15:12:48.228
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateTaskInChecklist"}	2019-08-15 15:12:59.084
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "SwitchToChecklistInRightSidebar"}	2019-08-15 15:19:55.591
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "CreateNestedTaskInKanban"}	2019-08-15 15:21:35.117
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "ReorderTaskInKanban"}	2019-08-15 15:21:38.46
247fe3ab-444d-fe00-1531-1aba7f4ff6f2	{"type": "ClickLoginInAuthStatus"}	2019-08-15 15:39:48.075
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "AcceptInvite"}	2019-08-15 15:41:10.009
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "ClickLogo"}	2019-08-15 15:41:30.85
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "ClickSignupInAuthStatus"}	2019-08-15 15:42:01.269
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "Signup"}	2019-08-15 15:42:13.037
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "SwitchPageFromExpandedLeftSidebar"}	2019-08-15 15:42:16.416
247fe3c7-6d89-2900-1531-1b012f3f05b9	{"type": "SwitchToKanbanInPageHeader"}	2019-08-15 15:43:11.764
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "ClickAvatarInAuthStatus"}	2019-08-15 15:47:43.339
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "EditMessageInRightSidebar"}	2019-08-15 15:47:49.851
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "ClickSignupInAuthStatus"}	2019-08-15 15:50:01.547
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "Signup"}	2019-08-15 15:50:11.464
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "AcceptInvite"}	2019-08-15 15:50:33.427
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "OpenTaskInRightSidebar"}	2019-08-15 15:50:45.478
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "ZoomIntoTask"}	2019-08-15 15:50:47.683
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "SwitchPageFromExpandedLeftSidebar"}	2019-08-15 15:51:13.38
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "ClickLogo"}	2019-08-15 15:51:38.633
247fe499-013f-9100-1531-1bf25ba12bee	{"type": "SwitchToKanbanInRightSidebar"}	2019-08-15 15:54:15.469
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "OpenMessageInRightSidebar"}	2019-08-16 16:42:13.197
246ded7f-ae61-db00-0beb-c341f9e67fd0	{"type": "ConvertNode"}	2019-08-16 16:42:40.982
\.


--
-- Data for Name: userdetail; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.userdetail (userid, email, verified) FROM stdin;
246ded7f-ae61-db00-0beb-c341f9e67fd0	manager@localhost	t
246dedc6-243a-0a00-0beb-c4d86329b3b4	klaus@localhost	t
246dedc9-7a4a-3d00-0beb-c52921a63779	laura@localhost	t
246dedca-929b-2d00-0beb-c3cb96965ce3	michael@localhost	t
246dedcb-974c-d900-0beb-c4e3a7d0dff6	marie@localhost	t
246dedcc-c055-d000-0beb-c4dbf20d81d0	petra@localhost	t
246dedcd-cd3d-0e00-0beb-c4d2e6f4f6be	harald@localhost	t
246dedcf-0d99-8e00-0beb-c4352b0feb3a	bernd@localhost	t
246dedd0-2236-3900-0beb-c3131fbba3b8	eva@localhost	t
246dedd1-194a-5d00-0beb-c5195068e4cc	felix@localhost	t
246dedd2-3468-e900-0beb-c4797c30fedf	sarah@localhost	t
247fe3c7-6d89-2900-1531-1b012f3f05b9	juliane@localhost	t
247fe499-013f-9100-1531-1bf25ba12bee	daniel@localhost	f
\.


--
-- Data for Name: webpushsubscription; Type: TABLE DATA; Schema: public; Owner: wust
--

COPY public.webpushsubscription (id, userid, endpointurl, p256dh, auth) FROM stdin;
\.


--
-- Name: webpushsubscription_id_seq; Type: SEQUENCE SET; Schema: public; Owner: wust
--

SELECT pg_catalog.setval('public.webpushsubscription_id_seq', 1, false);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: oauthclient oauthclient_userid_service_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.oauthclient
    ADD CONSTRAINT oauthclient_userid_service_key UNIQUE (userid, service);


--
-- Name: password password_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.password
    ADD CONSTRAINT password_pkey PRIMARY KEY (userid);


--
-- Name: node post_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.node
    ADD CONSTRAINT post_pkey PRIMARY KEY (id);


--
-- Name: usedfeature usedfeature_userid_feature_key; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.usedfeature
    ADD CONSTRAINT usedfeature_userid_feature_key UNIQUE (userid, feature);


--
-- Name: userdetail userdetail_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.userdetail
    ADD CONSTRAINT userdetail_pkey PRIMARY KEY (userid);


--
-- Name: webpushsubscription webpushsubscription_pkey; Type: CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.webpushsubscription
    ADD CONSTRAINT webpushsubscription_pkey PRIMARY KEY (id);


--
-- Name: edge_index; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX edge_index ON public.edge USING btree (sourceid, ((data ->> 'type'::text)), targetid);


--
-- Name: edge_unique_index; Type: INDEX; Schema: public; Owner: wust
--

CREATE UNIQUE INDEX edge_unique_index ON public.edge USING btree (sourceid, ((data ->> 'type'::text)), COALESCE((data ->> 'key'::text), ''::text), targetid) WHERE ((data ->> 'type'::text) <> 'Author'::text);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_edge_targetid; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX idx_edge_targetid ON public.edge USING btree (targetid);


--
-- Name: idx_edge_type; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX idx_edge_type ON public.edge USING btree (((data ->> 'type'::text)));


--
-- Name: node_can_access_mat_complete_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX node_can_access_mat_complete_idx ON public.node_can_access_mat USING btree (complete);


--
-- Name: node_can_access_mat_nodeid_userid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE UNIQUE INDEX node_can_access_mat_nodeid_userid_idx ON public.node_can_access_mat USING btree (nodeid, userid);


--
-- Name: node_can_access_mat_userid_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX node_can_access_mat_userid_idx ON public.node_can_access_mat USING btree (userid);


--
-- Name: node_expr_idx1; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX node_expr_idx1 ON public.node USING btree (((data ->> 'isImplicit'::text))) WHERE ((data ->> 'type'::text) = 'User'::text);


--
-- Name: node_expr_idx2; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX node_expr_idx2 ON public.node USING btree (((data ->> 'key'::text))) WHERE ((data ->> 'type'::text) = 'File'::text);


--
-- Name: oauthclient_service_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX oauthclient_service_idx ON public.oauthclient USING btree (service);


--
-- Name: post_content_type; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX post_content_type ON public.node USING btree (((data ->> 'type'::text)));


--
-- Name: rawpost_joinlevel_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE INDEX rawpost_joinlevel_idx ON public.node USING btree (accesslevel);


--
-- Name: unique_user_email; Type: INDEX; Schema: public; Owner: wust
--

CREATE UNIQUE INDEX unique_user_email ON public.userdetail USING btree (email) WHERE (email IS NOT NULL);


--
-- Name: webpushsubscription_endpointurl_p256dh_auth_idx; Type: INDEX; Schema: public; Owner: wust
--

CREATE UNIQUE INDEX webpushsubscription_endpointurl_p256dh_auth_idx ON public.webpushsubscription USING btree (endpointurl, p256dh, auth);


--
-- Name: edge edge_delete_trigger; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER edge_delete_trigger BEFORE DELETE ON public.edge FOR EACH ROW EXECUTE PROCEDURE public.edge_delete();


--
-- Name: edge edge_insert_trigger; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER edge_insert_trigger BEFORE INSERT ON public.edge FOR EACH ROW EXECUTE PROCEDURE public.edge_insert();


--
-- Name: edge edge_update_trigger; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER edge_update_trigger BEFORE INSERT ON public.edge FOR EACH ROW EXECUTE PROCEDURE public.edge_update();


--
-- Name: node node_update_trigger; Type: TRIGGER; Schema: public; Owner: wust
--

CREATE TRIGGER node_update_trigger BEFORE UPDATE ON public.node FOR EACH ROW EXECUTE PROCEDURE public.node_update();


--
-- Name: edge connection_sourceid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.edge
    ADD CONSTRAINT connection_sourceid_fkey FOREIGN KEY (sourceid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: edge connection_targetid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.edge
    ADD CONSTRAINT connection_targetid_fkey FOREIGN KEY (targetid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: password fk_user_post; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.password
    ADD CONSTRAINT fk_user_post FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: webpushsubscription fk_user_post; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.webpushsubscription
    ADD CONSTRAINT fk_user_post FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: node_can_access_mat node_can_access_mat_nodeid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.node_can_access_mat
    ADD CONSTRAINT node_can_access_mat_nodeid_fkey FOREIGN KEY (nodeid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: node_can_access_mat node_can_access_mat_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.node_can_access_mat
    ADD CONSTRAINT node_can_access_mat_userid_fkey FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: oauthclient oauthclient_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.oauthclient
    ADD CONSTRAINT oauthclient_userid_fkey FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: usedfeature usedfeature_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.usedfeature
    ADD CONSTRAINT usedfeature_userid_fkey FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- Name: userdetail userdetail_userid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: wust
--

ALTER TABLE ONLY public.userdetail
    ADD CONSTRAINT userdetail_userid_fkey FOREIGN KEY (userid) REFERENCES public.node(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

