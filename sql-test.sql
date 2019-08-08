-- run:
-- (with a staging dump from master)
-- find dbMigration/core/sql/V114__node_access_materialized.sql| entr -ncs './start pgrestore 2019-08-12-15-56-58-dev-postgres-backup.sql && cat dbMigration/core/sql/V114__node_access_materialized.sql | ./start psql && sleep 30 && cat sql-test.sql | ./start pgcli'


SET max_parallel_workers_per_gather = 0;

-- select count(*) from graph_page('{239cf479-bce1-5800-06e8-b5cf11be1640}'::uuid[], '239d3496-0481-0800-056f-eedb14244000'::uuid);
-- select funcname, calls, total_time, total_time/calls as total_avg, self_time, self_time/calls as self_avg from pg_stat_user_functions order by self_time DESC;



-- select pg_stat_reset();
-- select count(*) from graph_page('{239cf479-bce1-5800-06e8-b5cf11be1640}'::uuid[], '239d3496-0481-0800-056f-eedb14244000'::uuid);
-- select funcname, calls, total_time, total_time/calls as total_avg, self_time, self_time/calls as self_avg from pg_stat_user_functions order by self_time DESC;


-- ./start psql  -qAt -f sql-test.sql > analyze.json
-- https://dalibo.github.io/pev2
EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON)


with graph_traversed_page_nodes_cte as (
    with recursive content(id) AS (
        select id from node where id = any('{239cf479-bce1-5800-06e8-b5cf11be1640}') and node_can_access(id, '239d3496-0481-0800-056f-eedb14244000'::uuid) -- strangely this is faster than `select unnest(starts)`
        union -- discards duplicates, therefore handles cycles and diamond cases
        select contentedge.target_nodeid
            FROM content INNER JOIN contentedge ON contentedge.source_nodeid = content.id
            where node_can_access(contentedge.target_nodeid, '239d3496-0481-0800-056f-eedb14244000'::uuid)
    ),
    transitive_parents(id) AS (
        select id from node where id = any('{239cf479-bce1-5800-06e8-b5cf11be1640}') and node_can_access(id, '239d3496-0481-0800-056f-eedb14244000'::uuid)
        union
        select contentedge.source_nodeid
            FROM transitive_parents INNER JOIN contentedge ON contentedge.target_nodeid = transitive_parents.id
            where node_can_access(contentedge.source_nodeid, '239d3496-0481-0800-056f-eedb14244000'::uuid)
    )

    -- all transitive children
    select * from content
    union
    -- direct parents of content, useful to know tags of content nodes
    select edge.sourceid from content INNER JOIN edge ON edge.targetid = content.id where node_can_access(edge.sourceid, '239d3496-0481-0800-056f-eedb14244000'::uuid)
    union
    -- transitive parents describe the path/breadcrumbs to the page
    select * FROM transitive_parents
),


content_node_ids as (
        select id from (
            select * from user_bookmarks('239d3496-0481-0800-056f-eedb14244000'::uuid) as id -- all channels of user, inlining is slower
            union
            select * from graph_traversed_page_nodes_cte as id -- all nodes, specified by page (transitive children + transitive parents), inlining is slower
        ) as node_ids
    ),
    -- content node ids and users joined with node
    all_node_ids as (
        select id from content_node_ids
        union
        select useredge.target_userid as id from content_node_ids inner join useredge on useredge.source_nodeid = content_node_ids.id
        union
        select '239d3496-0481-0800-056f-eedb14244000'::uuid as id
    ),

    ---- induced subgraph of all nodes without edges - what kind of node has no edges?
    --select node.id, node.data, node.role, node.accesslevel, node.views, array[]::uuid[], array[]::text[]
    --from node
    --inner join all_node_ids on all_node_ids.id = node.id
    --where not exists(select 1 from edge where node.id = edge.sourceid)

    --union all

    -- induced subgraph of all nodes with edges
    result as (select node.id, node.data, node.role, node.accesslevel, node.views, -- all node columns
    array_agg(edge.targetid), array_agg(edge.data::text)
    from node
    inner join all_node_ids on all_node_ids.id = node.id
    inner join edge on edge.sourceid = node.id
    and exists (select 1 from all_node_ids where all_node_ids.id = edge.targetid)
    group by (node.id, node.data, node.role, node.accesslevel, node.views)) select count(*) from result; -- needed for multiple outgoing edges


-- select funcname, calls, total_time, total_time/calls as total_avg, self_time, self_time/calls as self_avg from pg_stat_user_functions order by self_time DESC;
