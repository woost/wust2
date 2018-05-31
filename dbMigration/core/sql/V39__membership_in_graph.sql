-- remove garbage
alter table "user" drop constraint fk_userpostid;
delete from post where post.id in (select userpostid from "user");

alter table connection drop constraint connection_sourceid_fkey; -- to put userids in connection table, readded later when users are merged into posts

-- for every post add connection for authorship
insert into connection (select author as sourceid, id as targetid, json_build_object('type', 'Author', 'timestamp', (extract(epoch from created ) * 1000 )::bigint) as content from "post");
alter table post drop column author;
alter table post drop column created;
alter table post drop column modified;

-- move users into posts
insert into post (select id, json_build_object('type', 'User', 'name', name, 'isImplicit', isimplicit, 'revision', revision, 'channelNodeId', channelpostid) as content from "user");
alter table connection add constraint "connection_sourceid_fkey" FOREIGN KEY (sourceid) REFERENCES post(id) ON DELETE CASCADE;


alter table connection drop constraint selfloop; -- users are members of their profiles
insert into connection (select userid as sourceid, postid as targetid, json_build_object('type', 'Member', 'level', level) as content from membership where membership.postid in (select id from post));
drop table membership;


alter table password drop constraint "password_id_fkey";
alter table password add constraint "fk_user_post" FOREIGN KEY (id) REFERENCES post(id) ON DELETE CASCADE;

alter table webpushsubscription drop constraint "webpushsubscription_userid_fkey";
alter table webpushsubscription add constraint "fk_user_post" FOREIGN KEY (userid) REFERENCES post(id) ON DELETE CASCADE;

drop table "user";

alter table "post" rename content to data;
alter table "connection" rename content to data;

alter table "post" rename to "node";
alter table "connection" rename to "edge";

alter table node
    ALTER COLUMN deleted DROP DEFAULT,
    ALTER COLUMN joindate DROP DEFAULT,
    ALTER COLUMN joinlevel DROP DEFAULT;


create unique index on node((data->>'name')) where data->>'type' = 'User';

update node set deleted = '4000-01-01 00:00:00' where deleted = '294276-01-01 00:00:00';
update node set deleted = '1970-01-01 00:00:00' where deleted = '0001-01-01 00:00:00';
update node set joindate = '4000-01-01 00:00:00' where joindate = '294276-01-01 00:00:00';
update node set joindate = '1970-01-01 00:00:00' where joindate = '0001-01-01 00:00:00';

--------------------
--- procedures
--------------------
-- DROP
drop function create_traversal_function;
drop function graph_page;
drop function graph_page_posts;
drop function graph_page_with_orphans;
drop function induced_subgraph;
drop function mergeFirstUserIntoSecond;
drop function notified_users;
drop function now_utc;
drop function readable_posts;
drop function traverse_children;
drop function traverse_parents;

drop aggregate array_merge_agg(anyarray);
drop function array_intersect;
drop function array_merge;

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
-- traversal over specified edges. Returns array of ids. Expects a temporary table 'visited (id varchar(36) NOT NULL)' to exist.
create function create_traversal_function(name text, edge regclass, edge_source text, parentlabel text, edge_target text, node_limit int) returns void as $$
begin
    -- TODO: benchmark array_length vs cardinality
    -- TODO: test if traversal only happens via provided label
EXECUTE '
create function ' || quote_ident(name) || '(start varchar(36)[], stop varchar(36)[] default array[]::varchar(36)[]) returns varchar(36)[] as $func$
declare
    queue varchar(36)[] := start;
begin
    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(select unnest(queue) except select unnest(stop)); -- stop traversal for ids in stop-array
        IF (select count(*) from visited) > '|| node_limit ||' THEN -- node limit reached, stop traversal
            queue := ARRAY[];
        ELSE
            queue := array(
                select distinct '|| quote_ident(edge_target) ||'
                from (select unnest(queue) as id) as q
                join '|| edge ||' on '|| quote_ident(edge_source) ||' = q.id and '|| edge ||'.data->>''type'' = '''|| parentlabel ||'''
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
$$ language plpgsql;






select create_traversal_function('traverse_children', 'edge', 'targetid', 'Parent', 'sourceid', 10000);

select create_traversal_function('traverse_parents', 'edge', 'sourceid', 'Parent', 'targetid', 10000);






-- traverses from parents and stops at specified children. returns visited node-ids (including parents and children)
create function graph_page_nodes(edge_parents varchar(36)[], edge_children varchar(36)[]) returns varchar(36)[] as $$
declare
    -- parents varchar(36)[];
    children varchar(36)[];
begin
    -- we have to privde the temporary visited table for the traversal functions,
    -- since it is not possible to create function local temporary tables.
    -- Creating is transaction local, which leads to "table 'visited' already exists" errors,
    -- when created inside functions.
    create temporary table visited (id varchar(36) NOT NULL) on commit drop;
    create unique index on visited (id);

    -- each traversal gets a prefilled set of visited vertices ( the ones where the traversal should stop )

    -- walk from edge_parents in source direction until hitting edge_children (includes both edge_parents and edge_children)
    children := traverse_children(edge_parents, edge_children); -- start traversal at parents, traversal stops at children

    /* truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges */

    return array( select distinct unnest(children)); -- return distinct nodeids
end;
$$ language plpgsql;






-- expects table visited
create function readable_nodes(requester_userid varchar(36), nodeids varchar(36)[], stopchildren varchar(36)[] default array[]::varchar(36)[]) returns varchar(36)[] as $$
declare
    transitive_parents varchar(36)[];
    readable_parents varchar(36)[];
    readable_transitive_parents varchar(36)[];
begin
    -- transitive_parents = traverse all nodeids up (over parent edge), collect all nodeids
    -- readable_parents = join transitive_parents with memebership and retain the ones with membership
    -- readable_transitive_parents = traverse readable_parents down and stop at lower page bound (stopchildren)
    -- return: intersect readable_transitive_parents with nodeids

    -- we have to privde the temporary visited table for the traversal functions,
    -- since it is not possible to create function local temporary tables.
    -- Creating is transaction local, which leads to "table 'visited' already exists" errors,
    -- when created inside functions.
--    create temporary table visited (id varchar(36) NOT NULL) on commit drop;
--    create unique index on visited (id);
    truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges

    transitive_parents := traverse_parents(nodeids);
    -- RAISE NOTICE 'transitive_parents: %', transitive_parents;

    readable_parents := array(select targetid from edge where data->>'type' = 'Member' and sourceid = requester_userid and targetid = any (transitive_parents));

    -- RAISE NOTICE 'readable_parents: %', readable_parents;

    truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges
    readable_transitive_parents := traverse_children(readable_parents, stopchildren); -- traversal stops at lower page bound
    -- RAISE NOTICE 'readable_transitive_parents: %', readable_transitive_parents;

    return array_intersect(readable_transitive_parents, nodeids);
end;
$$ language plpgsql;






create function induced_subgraph(nodeids varchar(36)[])
returns table(nodeid varchar(36), data jsonb, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], edgeData text[])
as $$
    select
    node.id, node.data, node.joindate, node.joinlevel, node.deleted, -- all node columns
    array_remove(array_agg(edge.targetid), NULL), array_remove(array_agg(edge.data::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from node
    left outer join edge on edge.sourceid = node.id AND edge.targetid = ANY(nodeids)
    where node.id = ANY(nodeids)
    group by (node.id, node.data, node.joindate, node.joinlevel, node.deleted) -- needed for multiple outgoing edges
$$ language sql;



-- page(parents, children) -> graph as adjacency list
create function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36))
returns table(nodeid varchar(36), data jsonb, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], edgeData text[])
as $$
select induced_subgraph(
    array(
        with
            cpid as (select (data->>'channelNodeId')::varchar(36) as channelnodeid from node where id = userid and data->>'type' = 'User'),
            nodes as (
                select cpid.channelnodeid as id from cpid  -- channel post of user, containing all user channels
                union
                select sourceid as id from edge,cpid where edge.targetid = cpid.channelnodeid and data->>'type' = 'Parent' -- all user channels
                union
                select unnest(readable_nodes(userid, graph_page_nodes(parents, children), children)) as id -- all nodes, which the user can read
            ),
            authors as (select edge.sourceid as authorid from edge left join nodes on edge.targetid = nodes.id where edge.data->>'type' = 'Author' or edge.data->>'type' = 'Member') -- all available authors for all nodes
        select id from nodes
        union
        select authorid from authors
    )
);
$$ language sql;




-- page(parents, children) -> graph as adjacency list
create function graph_page_with_orphans(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(nodeid varchar(36), data jsonb, joindate timestamp without time zone, joinlevel accesslevel, deleted timestamp without time zone, targetids varchar(36)[], edgeData text[]) as $$

 with cpid as (select (data->>'channelNodeId')::varchar(36) as channelnodeid from node where id = userid and data->>'type' = 'User')
    select induced_subgraph(
        array[cpid.channelnodeid] ||
        array(select sourceid from edge,cpid where targetid = cpid.channelnodeid and data->>'type' = 'Parent') ||
        readable_nodes(userid,
            array(select id from node where id not in (select sourceid from edge where data->>'type' = 'Parent')) ||
            graph_page_nodes(parents, children), children
        )
    )
    from cpid;

$$ language sql;








-- this works on nodes, not only users. maybe restrict?
CREATE FUNCTION mergeFirstUserIntoSecond(oldUser varchar(36), keepUser varchar(36)) RETURNS VOID AS $$
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
 BEGIN

 FOR table_record IN tables LOOP
    BEGIN
        execute format('update %I set %I = ''%I'' where %I = ''%I''', table_record.table, table_record.col, keepUser, table_record.col, oldUser);
    EXCEPTION WHEN unique_violation THEN
        --do nothing or write NULL means do nothing
    END;
    execute format('delete from %I where %I = ''%I''', table_record.table, table_record.col, oldUser);
 END LOOP;

 execute format('delete from node where id = ''%I'' and data->>''type'' = ''User''', oldUser);
 END;
$$ LANGUAGE plpgsql;






create function notified_users(start varchar(36)[]) returns table(userid varchar(36), nodeids varchar(36)[]) as $func$
declare
    queue varchar(36)[] := start;
    nextqueue varchar(36)[] := '{}';
    parentlabel text := 'Parent';
    -- items record;
    begin
        create temporary table visited (id varchar(36) NOT NULL, reason varchar(36)) on commit drop;
        create unique index on visited (id,reason);

        create temporary table reasons (id varchar(36) NOT NULL, reasons varchar(36)[] NOT NULL) on commit drop;
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
