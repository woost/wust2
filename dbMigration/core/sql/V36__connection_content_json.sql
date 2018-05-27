drop TRIGGER vct_insert on connection;
drop TRIGGER vct_update on connection;
drop TRIGGER vct_delete on connection;
drop view connection;
ALTER TABLE rawconnection RENAME TO connection;

ALTER TABLE connection
    ADD COLUMN content JSONB NOT NULL DEFAULT '{"type": "Parent" }'::jsonb;

ALTER TABLE connection
    drop constraint connection_pkey,
    ALTER COLUMN content DROP DEFAULT,
    drop column label;

CREATE UNIQUE index connection_unique_index on connection using btree(sourceId, (content->>'type'), targetId);

DROP TABLE label;

drop FUNCTION get_label_id;
drop FUNCTION insert_label;
drop FUNCTION vc_insert;
drop FUNCTION vc_update;
drop FUNCTION vc_delete;


-- page(parents, children) -> graph as adjacency list
drop function graph_page;
create or replace function graph_page(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], connectioncontents text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)   
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from connection,cpid where targetid = cpid.channelpostid and content->>'type' = 'Parent') ||
        readable_posts(userid, graph_page_posts(parents, children), children)
    )
    from cpid;

$$ language sql;

-- page(parents, children) -> graph as adjacency list
drop function graph_page_with_orphans;
create or replace function graph_page_with_orphans(parents varchar(36)[], children varchar(36)[], userid varchar(36)) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], connectioncontents text[]) as $$

 with cpid as (select channelpostid from "user" where id = userid)
    select induced_subgraph(
        array[cpid.channelpostid] ||
        array(select sourceid from connection,cpid where targetid = cpid.channelpostid and content->>'type' = 'Parent') ||
        readable_posts(userid, 
            array(select id from post where id not in (select sourceid from connection where content->>'type' = 'Parent')) ||
            graph_page_posts(parents, children), children
        )
    )
    from cpid;

$$ language sql;

drop function if exists notified_users(start varchar(36)[]);
create or replace function notified_users(start varchar(36)[]) returns table(userid varchar(36), postids varchar(36)[]) as $func$
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
                    join connection on sourceid = q.id and connection.content->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(queue) as id) as q
                    join connection on sourceid = q.id and connection.content->>'type' = parentlabel
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
                    join connection on sourceid = q.id and connection.content->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    group by targetid
                ) on conflict (id) do update set reasons = array_merge(reasons.reasons, excluded.reasons);

                nextqueue := array(
                    select distinct targetid
                    from (select unnest(nextqueue) as id) as q
                    join connection on sourceid = q.id and connection.content->>'type' = parentlabel
                    join reasons on reasons.id = q.id
                    left outer join visited on targetid = visited.id 
                    where visited.id is NULL or NOT (visited.reason = any(reasons.reasons))
                    limit 10000 -- also apply limit on node degree
                );

                -- RAISE NOTICE 'nextqueue: %', nextqueue;

                --         raise notice 'iiiii: %', array(select targetid
                    --         from (select unnest(queue) as id) as q
                    --         join connection on sourceid = q.id and connection.content->>'type' = parentlabel
                    --         join reasons on reasons.id = q.id);
                --         raise notice 'uuuuu: %', array(select reasons.reasons
                    --         from (select unnest(queue) as id) as q
                    --         join connection on sourceid = q.id and connection.content->>'type' = parentlabel
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

    return query select membership.userid, array_merge_agg(reasons)
    from reasons
    join membership on reasons.id = membership.postid
    group by membership.userid;
end;
$func$ language plpgsql;

-- this function generates traversal functions.
-- traversal over specified edges. Returns array of ids. Expects a temporary table 'visited (id varchar(36) NOT NULL)' to exist.
drop function create_traversal_function;
create or replace function create_traversal_function(name text, edge regclass, edge_source text, parentlabel text, edge_target text, node_limit int) returns void as $$
begin
    -- TODO: benchmark array_length vs cardinality
    -- TODO: test if traversal only happens via provided label
EXECUTE '
drop function if exists ' || quote_ident(name) || ';
create or replace function ' || quote_ident(name) || '(start varchar(36)[], stop varchar(36)[] default array[]::varchar(36)[]) returns varchar(36)[] as $func$
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
                join '|| edge ||' on '|| quote_ident(edge_source) ||' = q.id and '|| edge ||'.content->>''type'' = '''|| parentlabel ||'''
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

select create_traversal_function('traverse_children', 'connection', 'targetid', 'Parent', 'sourceid', 10000);

select create_traversal_function('traverse_parents', 'connection', 'sourceid', 'Parent', 'targetid', 10000);


-- traverses from parents and stops at specified children. returns visited postids (including parents and children)
create or replace function graph_page_posts(conn_parents varchar(36)[], conn_children varchar(36)[]) returns varchar(36)[] as $$
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

    -- walk from conn_parents in source direction until hitting conn_children (includes both conn_parents and conn_children)
    children := traverse_children(conn_parents, conn_children); -- start traversal at parents, traversal stops at children

    /* truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges */

    return array( select distinct unnest(children)); -- return distinct postids
end;
$$ language plpgsql;

drop function induced_subgraph;
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), content jsonb, author varchar(36), created timestamp without time zone, modified timestamp without time zone, joindate timestamp without time zone, joinlevel accesslevel, targetids varchar(36)[], connectionContents text[]) as $$

    select
    post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel, -- all post columns
    array_remove(array_agg(conn.targetid), NULL), array_remove(array_agg(conn.content::text), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from post
    left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
    where post.id = ANY(postids)
    group by (post.id, post.content, post.author, post.created, post.modified, post.joindate, post.joinlevel) -- needed for multiple outgoing connections

$$ language sql STABLE;
