update edge set data = '{"type":"Parent", "timestamp": null}'::jsonb where data->>'type' = 'Parent';
insert into edge(sourceid, targetid, data) (select sourceid, targetid, jsonb_build_object('deletedAt', data->>'timestamp', 'type', 'Parent') from edge where data->>'type' = 'DeletedParent') on conflict do nothing;
delete from edge where data->>'type' = 'DeletedParent';
