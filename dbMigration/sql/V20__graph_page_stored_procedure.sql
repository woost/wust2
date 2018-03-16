DROP FUNCTION graph_page_posts(character varying[],character varying[]);
DROP FUNCTION graph_page(character varying[],character varying[]);
create or replace function create_traversal_function(name text, edge regclass, edge_source text, edge_target text) returns void as $$
begin
EXECUTE '
create or replace function ' || quote_ident(name) || '(start varchar(36)[]) returns varchar(36)[] as $func$
declare
    queue varchar(36)[] := start;
begin
    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        queue := array(
            select '|| quote_ident(edge_target) ||'
            from (select unnest(queue) as id) as q
            join '|| edge ||' on '|| quote_ident(edge_source) ||' = q.id
            left outer join visited on '|| quote_ident(edge_target) ||' = visited.id
            where visited.id is NULL
        );
    END LOOP;
    return array (select id from visited);
end;
$func$ language plpgsql;
';
end
$$ language plpgsql;

select create_traversal_function('traverse_targets', 'connection', 'sourceid', 'targetid');
select create_traversal_function('traverse_sources', 'connection', 'targetid', 'sourceid');
select create_traversal_function('traverse_children', 'containment', 'parentid', 'childid');
select create_traversal_function('traverse_parents', 'containment', 'childid', 'parentid');

-- induced subgraph: postids -> ajacency list
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), title text, targetids varchar(36)[], childids varchar(36)[]) as $$
    select
    id, post.title,
    array_remove(array_agg(post.targetid), NULL),
    array_remove(array_agg(post.childid), NULL)

    from (
        select post.id, post.title, conn.targetid, NULL as childid
        from post
        left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
        where post.id = ANY(postids)

        UNION ALL

        select post.id, post.title, NULL as targetid, cont.childid
        from post
        left outer join containment cont on cont.parentid = post.id AND cont.childid = ANY(postids)
        where post.id = ANY(postids)
    ) post

    group by (post.id, post.title)
$$ language sql;


create or replace function graph_page_posts(conn_parents varchar(36)[], conn_children varchar(36)[]) returns varchar(36)[] as $$
declare
    children varchar(36)[];
    /* parents varchar(36)[]; */
begin
    -- we have to privde the temporary visited table for the traversal functions,
    -- since it is not possible to create function local temporary tables.
    -- Creating is transaction local, which leads to "table 'visited' already exists" errors,
    -- when created inside functions.
    create temporary table visited (id varchar(36) NOT NULL) on commit drop;
    create unique index on visited (id);

    -- each traversal gets a prefilled set of visited vertices ( the ones where the traversal should stop )

    -- walk from conn_children in source direction until hitting conn_parents (includes both conn_children and conn_parents)
    /* inser into visited (select unnest(conn_parents)) on conflict do nothing; */
    /* parents := traverse_parents(conn_children); */

    -- walk from conn_parents in target direction until hitting conn_children (includes both conn_parents and conn_children)
    /* truncate table visited; -- truncating is needed, because else traversal stops at the already visited vertices, even if they have further unvisited incoming edges */
    insert into visited (select unnest(conn_children)) on conflict do nothing;
    children := traverse_children(conn_parents);

    return array( select distinct unnest(children)); -- return distinct postids
end;
$$ language plpgsql;

create or replace function graph_page(conn_parents varchar(36)[], conn_children varchar(36)[]) returns table(postid varchar(36), title text, targetids varchar(36)[], childids varchar(36)[]) as $$
    select induced_subgraph(graph_page_posts(conn_parents, conn_children));
$$ language sql;


update post set title = id;
-- select * from post order by id;
-- select * from induced_subgraph(array(select id from post));

-- select * from traversetargets(array[412]);
-- select * from traversesources(array[412]);

-- select * from graph_page_posts(array[412, 417], array[419]);
select * from graph_page(array[$$cj4bsu54f0005y637ygyvwecl$$], array[$$cj4bsubrn0008y637f95px0cz$$]);
