-- this function generates traversal functions.
-- traversal over specified edges. Returns array of ids. Expects a temporary table 'visited (id varchar(36) NOT NULL)' to exist.
create or replace function create_traversal_function(name text, edge regclass, edge_source text, labelid int, edge_target text, node_limit int) returns void as $$
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

select create_traversal_function('traverse_children', 'rawconnection', 'targetid', (select id from label where name = 'parent'), 'sourceid', 10000);

select create_traversal_function('traverse_parents', 'rawconnection', 'sourceid', (select id from label where name = 'parent'), 'targetid', 10000);


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
