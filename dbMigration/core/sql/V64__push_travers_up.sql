create or replace function notified_nodes_for_user(userid uuid, start uuid[]) returns table(nodeid uuid) as $func$
declare
    notified_node_ids uuid[];
begin
    create temporary table visited (id uuid NOT NULL) on commit drop;
    create unique index on visited (id);
    notified_node_ids := (select array_agg(sourceid) from edge where data->>'type' = 'Notify' and targetid = userid);
    return query select id as nodeid from (select unnest(traverse_parents(start, notified_node_ids, userid)) id) ids;
end;
$func$ language plpgsql;
