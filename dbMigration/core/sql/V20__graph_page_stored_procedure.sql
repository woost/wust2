drop function graph_component;
--DROP FUNCTION graph_page_posts(character varying[], character varying[]);
--DROP FUNCTION graph_page(character varying[], character varying[]);

-- this function generates traversal functions.
-- traversal over specified edges. Returns array of ids. Expects a temporary table 'visited (id varchar(36) NOT NULL)' to exist.
create or replace function create_traversal_function(name text, edge regclass, edge_source text, labelid int, edge_target text, node_limit int) returns void as $$
begin
    -- TODO: benchmark array_length vs cardinality
    -- TODO: test if traversal only happens via provided label
EXECUTE '
create or replace function ' || quote_ident(name) || '(start varchar(36)[]) returns varchar(36)[] as $func$
declare
    queue varchar(36)[] := start;
begin
    WHILE array_length(queue,1) > 0 LOOP
        insert into visited (select unnest(queue)) on conflict do nothing;
        IF (select count(*) from visited) > '|| node_limit ||' THEN -- node limit reached, stop traversal
            queue := ARRAY[];
        ELSE
            queue := array(
                select '|| quote_ident(edge_target) ||'
                from (select unnest(queue) as id) as q
                join '|| edge ||' on '|| quote_ident(edge_source) ||' = q.id and '|| edge ||'.label = '|| labelid ||' 
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

select create_traversal_function('traverse_children', 'rawconnection', 'targetid', (select id from label where name = 'parent'), 'sourceid', 1000); -- stored in connection as: parent source/child --parent--> target/parent

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
    insert into visited (select unnest(conn_children)) on conflict do nothing; -- traversal stops at children
    children := traverse_children(conn_parents); -- start traversal at parents

    /* truncate table visited; -- truncating is needed, because else, the next traversal stops at the already visited vertices, even if they have further unvisited incoming edges */

    return array( select distinct unnest(children)); -- return distinct postids
end;
$$ language plpgsql;


-- induced subgraph: postids -> ajacency list
create or replace function induced_subgraph(postids varchar(36)[]) returns table(postid varchar(36), content text, author varchar(36), created timestamp without time zone, modified timestamp without time zone, targetids varchar(36)[], labels text[]) as $$

    select
    post.id, post.content, post.author, post.created, post.modified, -- all post columns
    array_remove(array_agg(conn.targetid), NULL), array_remove(array_agg(conn.label), NULL) -- removing NULL is important (e.g. decoding NULL as a string fails)
    from post
    left outer join connection conn on conn.sourceid = post.id AND conn.targetid = ANY(postids)
    where post.id = ANY(postids)
    group by (post.id, post.content, post.author, post.created, post.modified) -- needed for multiple outgoing connections

$$ language sql;


-- page(parents, children) -> graph as adjacency list
create or replace function graph_page(parents varchar(36)[], children varchar(36)[]) returns table(postid varchar(36), content text, author varchar(36), created timestamp without time zone, modified timestamp without time zone, targetids varchar(36)[], labels text[]) as $$
    select induced_subgraph(graph_page_posts(parents, children));
$$ language sql;


-- update post set content = id;
-- select * from post order by id;
-- select * from induced_subgraph(array(select id from post));

-- select * from traversetargets(array[412]);
-- select * from traversesources(array[412]);

-- select * from graph_page_posts(array[412, 417], array[419]);
-- select * from graph_page(array['cjeuhjyk50006z6rxghq9ct5s'], array['cjeuhm5f7000cz6rxluw75x0q']);
