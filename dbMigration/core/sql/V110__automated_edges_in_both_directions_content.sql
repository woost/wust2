create or replace view contentedge_reverse as
    select sourceid as source_nodeid, targetid as target_nodeid, data
    from edge
    where data->>'type' = any(
            array[ 'Automated' ]
    );

create or replace function graph_traversed_page_nodes(page_parents uuid[], userid uuid) returns setof uuid as $$
declare
    accessible_page_parents uuid[];
begin
    accessible_page_parents := (select array_agg(id) from node where id = any(page_parents) and can_access_node_expecting_cache_table(userid, id));

    return query
    with recursive
        content(id) AS (
            select id from node where id = any(accessible_page_parents) -- strangely this is faster than `select unnest(starts)`
            union -- discards duplicates, therefore handles cycles and diamond cases
            select contentedgeselect.target
                FROM content INNER JOIN (
                    select contentedge.source_nodeid as source, contentedge.target_nodeid as target from contentedge where contentedge.source_nodeid = content.id
                    union
                    select contentedge_reverse.target_nodeid as source, contentedge_reverse.source_nodeid as target from contentedge_reverse  where contentedge_reverse.target_nodeid = content.id
                ) as contentedgeselect on can_access_node_expecting_cache_table(userid, contentedgeselect.target)
        ),
        transitive_parents(id) AS (
            select id from node where id = any(accessible_page_parents)
            union
            select contentedge.source_nodeid
                FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
                    and can_access_node_expecting_cache_table(userid, contentedge.source_nodeid)
        )

        -- all transitive children
        select * from content
        union
        -- direct parents of content, useful to know tags of content nodes
        select edge.sourceid from content INNER JOIN edge ON edge.targetid = content.id and can_access_node_expecting_cache_table(userid, edge.sourceid)
        union
        -- transitive parents describe the path/breadcrumbs to the page
        select * FROM transitive_parents;
end
$$ language plpgsql strict;

