-- note: added distinct to queue
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

select create_traversal_function('traverse_children', 'rawconnection', 'targetid', (select id from label where name = 'parent'), 'sourceid', 10000); -- bump limit from 1000 to 10000, distinct queue

